import React, { createContext, useContext, useState } from 'react'

interface ChatInputContextValue {
  value: string
  setValue: (v: string) => void
}

const ChatInputContext = createContext<ChatInputContextValue | null>(null)

export function ChatInputProvider({ children }: { children: React.ReactNode }) {
  const [value, setValue] = useState('')
  // 使用 createElement 避免在 .ts 文件中直接写 JSX
  return React.createElement(
    ChatInputContext.Provider,
    { value: { value, setValue } },
    children,
  )
}

export function useChatInput(): ChatInputContextValue {
  const ctx = useContext(ChatInputContext)
  if (!ctx) {
    throw new Error('useChatInput must be used within a ChatInputProvider')
  }
  return ctx
}

