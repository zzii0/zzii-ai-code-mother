import { ref } from 'vue'
import { message } from 'ant-design-vue'
import request from '@/request'

export type ChatHistoryExportMode = 'full' | 'compact'

export function useChatHistoryExport(appId: () => string | number | undefined) {
  const exporting = ref(false)

  const exportChatHistory = async (exportMode: ChatHistoryExportMode) => {
    const id = appId()
    if (!id) {
      message.error('应用ID不存在')
      return
    }

    exporting.value = true
    try {
      const baseURL = request.defaults.baseURL || ''
      const url = `${baseURL}/chatHistory/app/${id}/export?exportMode=${exportMode}`
      const response = await fetch(url, {
        method: 'GET',
        credentials: 'include',
      })
      if (!response.ok) {
        const contentType = response.headers.get('Content-Type') || ''
        if (contentType.includes('application/json')) {
          const data = await response.json()
          throw new Error(data.message || '导出失败')
        }
        throw new Error(`导出失败: ${response.status}`)
      }

      const contentDisposition = response.headers.get('Content-Disposition')
      const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `chat-${id}.txt`
      const blob = await response.blob()
      const downloadUrl = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = downloadUrl
      link.download = fileName
      link.click()
      URL.revokeObjectURL(downloadUrl)
      message.success(exportMode === 'compact' ? '精简版对话导出成功' : '完整版对话导出成功')
    } catch (error) {
      console.error('导出失败：', error)
      const errorMessage = error instanceof Error ? error.message : '导出失败，请重试'
      message.error(errorMessage)
    } finally {
      exporting.value = false
    }
  }

  return {
    exporting,
    exportChatHistory,
  }
}
