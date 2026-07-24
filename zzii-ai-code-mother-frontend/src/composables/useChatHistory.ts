import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { listAppChatHistory } from '@/api/chatHistoryController'
import type { ChatMessage } from '@/types/chat'
import { getApiData } from '@/utils/apiHelper'

export function useChatHistory(appId: () => string | number | undefined) {
  const messages = ref<ChatMessage[]>([])
  const loadingHistory = ref(false)
  const hasMoreHistory = ref(false)
  const lastCreateTime = ref<string>()
  const historyLoaded = ref(false)

  const loadChatHistory = async (isLoadMore = false) => {
    const id = appId()
    if (!id || loadingHistory.value) return

    loadingHistory.value = true
    try {
      const params: API.listAppChatHistoryParams = {
        appId: id as number,
        pageSize: 10,
      }
      if (isLoadMore && lastCreateTime.value) {
        params.lastCreateTime = lastCreateTime.value
      }

      const res = await listAppChatHistory(params)
      const pageData = getApiData(res)
      const chatHistories = pageData?.records || []

      if (chatHistories.length > 0) {
        const historyMessages: ChatMessage[] = chatHistories
          .map((chat, index) => ({
            id: chat.id ?? `history-${chat.createTime}-${index}`,
            type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
            content: chat.message || '',
            createTime: chat.createTime,
          }))
          .reverse()

        if (isLoadMore) {
          messages.value.unshift(...historyMessages)
        } else {
          messages.value = historyMessages
        }

        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
        hasMoreHistory.value = chatHistories.length === 10
      } else {
        hasMoreHistory.value = false
      }
    } catch (error) {
      console.error('加载对话历史失败：', error)
      message.error('加载对话历史失败')
    } finally {
      // 首次加载无论成功与否都应标记完成，避免阻塞新应用的初始代码生成
      if (!isLoadMore) {
        historyLoaded.value = true
      }
      loadingHistory.value = false
    }
  }

  const loadMoreHistory = async () => {
    await loadChatHistory(true)
  }

  return {
    messages,
    loadingHistory,
    hasMoreHistory,
    historyLoaded,
    loadChatHistory,
    loadMoreHistory,
  }
}
