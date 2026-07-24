import { ref, type Ref } from 'vue'
import { message } from 'ant-design-vue'
import request from '@/request'
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

export function useChatStream(options: UseChatStreamOptions) {
  const userInput = ref('')
  const isGenerating = ref(false)

  const handleError = (error: unknown, aiMessageIndex: number) => {
    console.error('生成代码失败：', error)
    options.messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。'
    options.messages.value[aiMessageIndex].loading = false
    options.messages.value[aiMessageIndex].streaming = false
    message.error('生成失败，请重试')
    isGenerating.value = false
  }

  const generateCode = async (userMessage: string, aiMessageIndex: number) => {
    let eventSource: EventSource | null = null
    let streamCompleted = false
    let fullContent = ''
    let lastUpdateTime = 0
    let scrollScheduled = false
    let flushTimer: ReturnType<typeof setTimeout> | null = null

    const flushContent = (forceScroll = false) => {
      const aiMessage = options.messages.value[aiMessageIndex]
      aiMessage.content = fullContent
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
        if (flushTimer) {
          clearTimeout(flushTimer)
          flushTimer = null
        }
        lastUpdateTime = now
        flushContent()
        return
      }

      if (flushTimer) return

      flushTimer = setTimeout(() => {
        flushTimer = null
        lastUpdateTime = Date.now()
        flushContent()
      }, STREAM_UPDATE_INTERVAL - elapsed)
    }

    const completeStream = async () => {
      if (flushTimer) {
        clearTimeout(flushTimer)
        flushTimer = null
      }
      const aiMessage = options.messages.value[aiMessageIndex]
      aiMessage.content = fullContent
      aiMessage.loading = false
      isGenerating.value = false
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

      eventSource = new EventSource(url, { withCredentials: true })

      eventSource.onmessage = (event) => {
        if (streamCompleted) return
        try {
          const parsed = JSON.parse(event.data)
          const content = parsed.d
          if (content !== undefined && content !== null) {
            fullContent += content
            scheduleFlush()
          }
        } catch (error) {
          console.error('解析消息失败:', error)
          handleError(error, aiMessageIndex)
        }
      }

      eventSource.addEventListener('done', () => {
        if (streamCompleted) return
        streamCompleted = true
        eventSource?.close()
        void completeStream()
      })

      eventSource.addEventListener('business-error', (event: MessageEvent) => {
        if (streamCompleted) return
        if (flushTimer) {
          clearTimeout(flushTimer)
          flushTimer = null
        }
        try {
          const errorData = JSON.parse(event.data)
          const errorMessage = errorData.message || '生成过程中出现错误'
          options.messages.value[aiMessageIndex].content = `❌ ${errorMessage}`
          options.messages.value[aiMessageIndex].loading = false
          options.messages.value[aiMessageIndex].streaming = false
          message.error(errorMessage)
          streamCompleted = true
          isGenerating.value = false
          eventSource?.close()
        } catch (parseError) {
          console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
          handleError(new Error('服务器返回错误'), aiMessageIndex)
        }
      })

      eventSource.onerror = () => {
        if (streamCompleted || !isGenerating.value) return
        if (eventSource?.readyState === EventSource.CONNECTING) {
          streamCompleted = true
          eventSource?.close()
          void completeStream()
        } else {
          handleError(new Error('SSE连接错误'), aiMessageIndex)
        }
      }
    } catch (error) {
      console.error('创建 EventSource 失败：', error)
      handleError(error, aiMessageIndex)
    }
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
  }
}
