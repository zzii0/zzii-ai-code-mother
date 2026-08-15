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
  scrollToBottom: () => void
  onStreamComplete: () => void | Promise<void>
  getSelectedElement: () => ElementInfo | null
  clearSelectedElement: () => void
  exitEditModeIfNeeded: () => void
}

/** 流式更新节流间隔（毫秒） */
const STREAM_UPDATE_INTERVAL = 120

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
}

/**
 * AI 聊天流式生成 composable。
 * 支持：SSE 实时展示、手动停止（调后端 /app/chat/gen/stop + 关闭 EventSource）。
 */
export function useChatStream(options: UseChatStreamOptions) {
  const userInput = ref('')
  /** 是否正在生成（控制输入框禁用、显示停止按钮） */
  const isGenerating = ref(false)
  /** 当前活跃的 SSE 上下文；无生成时为 null */
  const activeStream = ref<ActiveStreamContext | null>(null)

  const clearFlushTimer = (ctx: ActiveStreamContext) => {
    if (ctx.flushTimer) {
      clearTimeout(ctx.flushTimer)
      ctx.flushTimer = null
    }
  }

  const handleError = (error: unknown, aiMessageIndex: number) => {
    console.error('生成代码失败：', error)
    options.messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。'
    options.messages.value[aiMessageIndex].loading = false
    options.messages.value[aiMessageIndex].streaming = false
    message.error('生成失败，请重试')
    isGenerating.value = false
    activeStream.value = null
  }

  /** 用户主动停止后的前端收尾：保留已生成文本，关闭 SSE，恢复可输入状态 */
  const finalizeStoppedGeneration = (ctx: ActiveStreamContext) => {
    clearFlushTimer(ctx)
    const aiMessage = options.messages.value[ctx.aiMessageIndex]
    // 保留已流式展示的内容；若尚未收到任何 chunk，则显示占位提示
    aiMessage.content = ctx.fullContent || aiMessage.content || '（已停止生成）'
    aiMessage.loading = false
    aiMessage.streaming = false
    isGenerating.value = false
    ctx.eventSource?.close()
    activeStream.value = null
    options.scrollToBottom()
    message.info('已停止生成')
  }

  const generateCode = async (userMessage: string, aiMessageIndex: number) => {
    const ctx: ActiveStreamContext = {
      completed: false,
      aiMessageIndex,
      fullContent: '',
      eventSource: null,
      flushTimer: null,
    }
    activeStream.value = ctx

    let lastUpdateTime = 0
    let scrollScheduled = false

    const flushContent = (forceScroll = false) => {
      const aiMessage = options.messages.value[aiMessageIndex]
      aiMessage.content = ctx.fullContent
      aiMessage.loading = false
      aiMessage.streaming = true

      if (forceScroll || !scrollScheduled) {
        scrollScheduled = true
        requestAnimationFrame(() => {
          scrollScheduled = false
          options.scrollToBottom()
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
      isGenerating.value = false
      activeStream.value = null
      options.scrollToBottom()
      await options.onStreamComplete()
      aiMessage.streaming = false
      options.scrollToBottom()
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
   * 注意：DeepSeek 上游 HTTP 可能仍会跑一会儿（方案 B 的已知限制）
   */
  const stopGeneration = async () => {
    const ctx = activeStream.value
    if (!isGenerating.value || !ctx || ctx.completed) {
      return
    }
    // 先标记完成，避免后续 SSE done/error 再次走 completeStream
    ctx.completed = true
    try {
      await stopChatGenerationApi({ appId: String(options.appId() || '') })
    } catch (error) {
      console.error('停止生成请求失败：', error)
      // 即使后端停止失败，前端也要关掉展示流，避免卡住「生成中」状态
    }
    finalizeStoppedGeneration(ctx)
  }

  const startGeneration = async (prompt: string) => {
    options.messages.value.push({ type: 'user', content: prompt })
    const aiMessageIndex = options.messages.value.length
    options.messages.value.push({ type: 'ai', content: '', loading: true, streaming: true })
    options.scrollToBottom()
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
    sendMessage,
    sendInitialMessage,
    stopGeneration,
  }
}
