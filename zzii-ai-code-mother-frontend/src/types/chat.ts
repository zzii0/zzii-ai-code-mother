export interface ChatMessage {
  id?: string | number
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  /** 流式输出中，使用纯文本展示以避免频繁 Markdown 渲染卡顿 */
  streaming?: boolean
  createTime?: string
}

export function createMessageKey(message: ChatMessage, index: number): string | number {
  return message.id ?? `${message.type}-${message.createTime ?? index}`
}
