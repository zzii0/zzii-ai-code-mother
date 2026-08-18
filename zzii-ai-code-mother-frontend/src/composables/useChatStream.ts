import { ref, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import request from '@/request'
import { stopChatGeneration as stopChatGenerationApi } from '@/api/appController'
import { API_BASE_URL } from '@/config/env'
import type { ChatMessage } from '@/types/chat'
import type { ElementInfo } from '@/utils/visualEditor'

interface UseChatStreamOptions {
  appId: () => string | number | undefined
  messages: Ref<ChatMessage[]>
  scrollToBottom: (instant?: boolean) => void
  onStreamComplete: (result: { previewReady: boolean }) => void | Promise<void>
  /** 构建/校验成功后立即刷新预览，不必等 SSE done */
  onPreviewReady?: () => void | Promise<void>
  getSelectedElement: () => ElementInfo | null
  clearSelectedElement: () => void
  exitEditModeIfNeeded: () => void
}

/** 流式更新节流间隔（毫秒） */
const STREAM_UPDATE_INTERVAL = 120

export type BuildPhase =
  | 'idle'
  | 'generating'
  | 'building'
  | 'validating'
  | 'fixing'
  | 'success'
  | 'failed'

/**
 * 当前这一次 SSE 生成的运行时上下文。
 * 用于在「停止」时关闭 EventSource、保留已展示内容、重置 UI 状态。
 */
interface ActiveStreamContext {
  /** 是否已结束（正常 done / 错误 / 用户停止） */
  completed: boolean
  /** 聊天列表中对应 AI 气泡的下标 */
  aiMessageIndex: number
  /** 已收到的完整流式文本 */
  fullContent: string
  eventSource: EventSource | null
  flushTimer: ReturnType<typeof setTimeout> | null
  /** 后处理（Vue 构建 / 原生校验保存）是否最终成功；无后处理时为 true */
  buildSucceeded: boolean
  /** 是否进入后处理流水线（Vue 构建或原生校验） */
  postProcessPipeline: boolean
}

/**
 * AI 聊天流式生成 composable。
 * 支持：SSE 实时展示、Vue 构建事件、原生 HTML/多文件校验事件、手动停止。
 */
export function useChatStream(options: UseChatStreamOptions) {
  const userInput = ref('')
  /** 是否正在生成（控制输入框禁用、显示停止按钮） */
  const isGenerating = ref(false)
  /** Vue 构建阶段（预览区文案） */
  const buildPhase = ref<BuildPhase>('idle')
  const buildStatusText = ref('')
  const buildError = ref('')
  /** 当前活跃的 SSE 上下文；无生成时为 null */
  const activeStream = ref<ActiveStreamContext | null>(null)

  const clearFlushTimer = (ctx: ActiveStreamContext) => {
    if (ctx.flushTimer) {
      clearTimeout(ctx.flushTimer)
      ctx.flushTimer = null
    }
  }

  const resetBuildUi = () => {
    buildPhase.value = 'idle'
    buildStatusText.value = ''
    buildError.value = ''
  }

  const handleError = (error: unknown, aiMessageIndex: number) => {
    console.error('生成代码失败：', error)
    options.messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。'
    options.messages.value[aiMessageIndex].loading = false
    options.messages.value[aiMessageIndex].streaming = false
    message.error('生成失败，请重试')
    isGenerating.value = false
    buildPhase.value = 'failed'
    activeStream.value = null
  }

  /** 用户主动停止后的前端收尾：保留已生成文本，关闭 SSE，恢复可输入状态 */
  const finalizeStoppedGeneration = (ctx: ActiveStreamContext) => {
    clearFlushTimer(ctx)
    const aiMessage = options.messages.value[ctx.aiMessageIndex]
    aiMessage.content = ctx.fullContent || aiMessage.content || '（已停止生成）'
    aiMessage.loading = false
    aiMessage.streaming = false
    isGenerating.value = false
    buildPhase.value = 'idle'
    buildStatusText.value = ''
    ctx.eventSource?.close()
    activeStream.value = null
    options.scrollToBottom(true)
    message.info('已停止生成')
  }

  const parseBuildEventPayload = (raw: string): Record<string, unknown> => {
    try {
      return JSON.parse(raw || '{}') as Record<string, unknown>
    } catch {
      return {}
    }
  }

  const generateCode = async (userMessage: string, aiMessageIndex: number) => {
    const ctx: ActiveStreamContext = {
      completed: false,
      aiMessageIndex,
      fullContent: '',
      eventSource: null,
      flushTimer: null,
      buildSucceeded: true,
      postProcessPipeline: false,
    }
    activeStream.value = ctx
    buildPhase.value = 'generating'
    buildStatusText.value = '正在生成网站...'
    buildError.value = ''

    let lastUpdateTime = 0
    let scrollScheduled = false

    const flushContent = (forceScroll = false) => {
      const aiMessage = options.messages.value[aiMessageIndex]
      aiMessage.content = ctx.fullContent
      aiMessage.loading = false
      aiMessage.streaming = true

      // 流式输出始终贴底，使用 instant 避免 smooth 跟不上长内容
      if (forceScroll || !scrollScheduled) {
        scrollScheduled = true
        requestAnimationFrame(() => {
          scrollScheduled = false
          options.scrollToBottom(true)
        })
      }
    }

    const scheduleFlush = () => {
      const now = Date.now()
      const elapsed = now - lastUpdateTime

      if (elapsed >= STREAM_UPDATE_INTERVAL) {
        clearFlushTimer(ctx)
        lastUpdateTime = now
        flushContent()
        return
      }

      if (ctx.flushTimer) return

      ctx.flushTimer = setTimeout(() => {
        ctx.flushTimer = null
        lastUpdateTime = Date.now()
        flushContent()
      }, STREAM_UPDATE_INTERVAL - elapsed)
    }

    const completeStream = async () => {
      clearFlushTimer(ctx)
      const aiMessage = options.messages.value[aiMessageIndex]
      aiMessage.content = ctx.fullContent
      aiMessage.loading = false
      options.scrollToBottom(true)
      const previewReady = !ctx.postProcessPipeline || ctx.buildSucceeded
      if (ctx.postProcessPipeline && ctx.buildSucceeded) {
        buildPhase.value = 'success'
        buildStatusText.value = buildStatusText.value || '已就绪'
      } else if (ctx.postProcessPipeline && !ctx.buildSucceeded) {
        buildPhase.value = 'failed'
      } else {
        resetBuildUi()
      }
      // 先刷新预览再结束 generating，避免预览区闪「占位空白」
      await options.onStreamComplete({ previewReady })
      isGenerating.value = false
      activeStream.value = null
      aiMessage.streaming = false
      options.scrollToBottom(true)
    }

    const triggerPreviewReady = () => {
      if (options.onPreviewReady) {
        void options.onPreviewReady()
      }
    }

    try {
      const baseURL = request.defaults.baseURL || API_BASE_URL
      const params = new URLSearchParams({
        appId: String(options.appId() || ''),
        userMessage,
      })
      const url = `${baseURL}/app/chat/gen/code?${params}`

      ctx.eventSource = new EventSource(url, { withCredentials: true })

      ctx.eventSource.onmessage = (event) => {
        if (ctx.completed) return
        try {
          const parsed = JSON.parse(event.data)
          const content = parsed.d
          if (content !== undefined && content !== null) {
            ctx.fullContent += content
            scheduleFlush()
          }
        } catch (error) {
          console.error('解析消息失败:', error)
          ctx.completed = true
          handleError(error, aiMessageIndex)
        }
      }

      ctx.eventSource.addEventListener('generation_done', () => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        buildPhase.value = 'building'
        buildStatusText.value = '代码已生成，正在准备预览...'
        clearFlushTimer(ctx)
        flushContent(true)
        options.scrollToBottom(true)
      })

      ctx.eventSource.addEventListener('build_start', (event: MessageEvent) => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        const payload = parseBuildEventPayload(event.data)
        const attempt = Number(payload.attempt || 1)
        buildPhase.value = 'building'
        buildStatusText.value = attempt > 1 ? `正在重新构建（第 ${attempt} 次）...` : '正在构建预览...'
        buildError.value = ''
        options.scrollToBottom(true)
      })

      ctx.eventSource.addEventListener('build_failed', (event: MessageEvent) => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        ctx.buildSucceeded = false
        const payload = parseBuildEventPayload(event.data)
        const file = String(payload.errorFile || '')
        const line = payload.errorLine ? `:${payload.errorLine}` : ''
        const err = String(payload.errorMessage || '构建失败')
        buildError.value = file ? `${file}${line} — ${err}` : err
        buildPhase.value = 'building'
        buildStatusText.value = '构建失败，准备自动修复...'
        options.scrollToBottom(true)
      })

      ctx.eventSource.addEventListener('build_fixing', (event: MessageEvent) => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        const payload = parseBuildEventPayload(event.data)
        const file = String(payload.errorFile || '出错文件')
        buildPhase.value = 'fixing'
        buildStatusText.value = `正在自动修复 ${file}...`
        options.scrollToBottom(true)
      })

      ctx.eventSource.addEventListener('build_success', () => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        ctx.buildSucceeded = true
        buildPhase.value = 'success'
        buildStatusText.value = '构建成功，正在刷新预览...'
        buildError.value = ''
        triggerPreviewReady()
        options.scrollToBottom(true)
      })

      ctx.eventSource.addEventListener('build_give_up', (event: MessageEvent) => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        ctx.buildSucceeded = false
        const payload = parseBuildEventPayload(event.data)
        const file = String(payload.errorFile || '')
        const err = String(payload.errorMessage || '多次自动修复后仍构建失败')
        buildPhase.value = 'failed'
        buildError.value = file ? `${file} — ${err}` : err
        buildStatusText.value = '自动修复未成功'
        message.warning('Vue 项目构建失败，请查看对话中的错误信息')
        options.scrollToBottom(true)
      })

      // 原生 HTML / 多文件：校验与定向补生成
      ctx.eventSource.addEventListener('validate_start', () => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        buildPhase.value = 'validating'
        buildStatusText.value = '正在校验生成的代码...'
        buildError.value = ''
        options.scrollToBottom(true)
      })

      ctx.eventSource.addEventListener('validate_failed', (event: MessageEvent) => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        ctx.buildSucceeded = false
        const payload = parseBuildEventPayload(event.data)
        const file = String(payload.errorFile || '')
        const err = String(payload.errorMessage || '代码校验未通过')
        buildError.value = file ? `${file} — ${err}` : err
        buildPhase.value = 'validating'
        buildStatusText.value = '校验未通过，准备自动补生成...'
        options.scrollToBottom(true)
      })

      ctx.eventSource.addEventListener('artifact_fixing', (event: MessageEvent) => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        const payload = parseBuildEventPayload(event.data)
        const file = String(payload.errorFile || '问题文件')
        buildPhase.value = 'fixing'
        buildStatusText.value = `正在自动补生成 ${file}...`
        options.scrollToBottom(true)
      })

      ctx.eventSource.addEventListener('save_success', () => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        ctx.buildSucceeded = true
        buildPhase.value = 'success'
        buildStatusText.value = '校验通过，正在刷新预览...'
        buildError.value = ''
        triggerPreviewReady()
        options.scrollToBottom(true)
      })

      ctx.eventSource.addEventListener('save_give_up', (event: MessageEvent) => {
        if (ctx.completed) return
        ctx.postProcessPipeline = true
        ctx.buildSucceeded = false
        const payload = parseBuildEventPayload(event.data)
        const file = String(payload.errorFile || '')
        const err = String(payload.errorMessage || '自动补生成后仍未通过校验')
        buildPhase.value = 'failed'
        buildError.value = file ? `${file} — ${err}` : err
        buildStatusText.value = '自动补生成未成功'
        message.warning('代码校验未通过，请查看对话中的错误信息')
        options.scrollToBottom(true)
      })

      ctx.eventSource.addEventListener('done', () => {
        if (ctx.completed) return
        ctx.completed = true
        ctx.eventSource?.close()
        void completeStream()
      })

      ctx.eventSource.addEventListener('business-error', (event: MessageEvent) => {
        if (ctx.completed) return
        clearFlushTimer(ctx)
        try {
          const errorData = JSON.parse(event.data)
          const errorMessage = errorData.message || '生成过程中出现错误'
          options.messages.value[aiMessageIndex].content = `❌ ${errorMessage}`
          options.messages.value[aiMessageIndex].loading = false
          options.messages.value[aiMessageIndex].streaming = false
          message.error(errorMessage)
          ctx.completed = true
          isGenerating.value = false
          buildPhase.value = 'failed'
          ctx.eventSource?.close()
          activeStream.value = null
        } catch (parseError) {
          console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
          ctx.completed = true
          handleError(new Error('服务器返回错误'), aiMessageIndex)
        }
      })

      ctx.eventSource.onerror = () => {
        if (ctx.completed || !isGenerating.value) return
        if (ctx.eventSource?.readyState === EventSource.CONNECTING) {
          ctx.completed = true
          ctx.eventSource?.close()
          void completeStream()
        }
      }
    } catch (error) {
      console.error('创建 EventSource 失败：', error)
      ctx.completed = true
      handleError(error, aiMessageIndex)
    }
  }

  /**
   * 手动停止生成。
   * 1) 先调后端 stop API，让服务端 cancel 任务、跳过代码保存
   * 2) 再关闭本地 EventSource，立即停止前端展示更新
   */
  const stopGeneration = async () => {
    const ctx = activeStream.value
    if (!isGenerating.value || !ctx || ctx.completed) {
      return
    }
    ctx.completed = true
    try {
      await stopChatGenerationApi({ appId: String(options.appId() || '') })
    } catch (error) {
      console.error('停止生成请求失败：', error)
    }
    finalizeStoppedGeneration(ctx)
  }

  const startGeneration = async (prompt: string) => {
    options.messages.value.push({ type: 'user', content: prompt })
    const aiMessageIndex = options.messages.value.length
    options.messages.value.push({ type: 'ai', content: '', loading: true, streaming: true })
    options.scrollToBottom(true)
    isGenerating.value = true
    await generateCode(prompt, aiMessageIndex)
  }

  const sendInitialMessage = async (prompt: string) => {
    await startGeneration(prompt)
  }

  const sendMessage = async () => {
    if (!userInput.value.trim() || isGenerating.value) return

    let content = userInput.value.trim()
    const selectedElement = options.getSelectedElement()
    if (selectedElement) {
      let elementContext = `\n\n选中元素信息：`
      if (selectedElement.pagePath) {
        elementContext += `\n- 页面路径: ${selectedElement.pagePath}`
      }
      elementContext += `\n- 标签: ${selectedElement.tagName.toLowerCase()}\n- 选择器: ${selectedElement.selector}`
      if (selectedElement.textContent) {
        elementContext += `\n- 当前内容: ${selectedElement.textContent.substring(0, 100)}`
      }
      content += elementContext
    }

    userInput.value = ''
    if (selectedElement) {
      options.clearSelectedElement()
      options.exitEditModeIfNeeded()
    }

    await startGeneration(content)
  }

  return {
    userInput,
    isGenerating,
    buildPhase,
    buildStatusText,
    buildError,
    sendMessage,
    sendInitialMessage,
    stopGeneration,
  }
}
