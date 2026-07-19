export interface ChatMessage {
  id?: string | number
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
}

export function createMessageKey(message: ChatMessage, index: number): string | number {
  return message.id ?? `${message.type}-${message.createTime ?? index}`
}
