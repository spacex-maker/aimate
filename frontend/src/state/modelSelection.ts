import React, { createContext, useContext, useState } from 'react'

export type ModelSource = 'SYSTEM' | 'USER_KEY'

export interface ModelSelection {
  source: ModelSource
  systemModelId?: number | null
  userApiKeyId?: number | null
}

interface ModelSelectionContextValue {
  selection: ModelSelection | null
  setSelection: (selection: ModelSelection) => void
}

const ModelSelectionContext = createContext<ModelSelectionContextValue | null>(null)

export function ModelSelectionProvider({ children }: { children: React.ReactNode }) {
  const [selection, setSelection] = useState<ModelSelection | null>(null)

  // 使用 createElement 避免在 .ts 文件中直接写 JSX，兼容当前 tsconfig 设置
  return React.createElement(
    ModelSelectionContext.Provider,
    { value: { selection, setSelection } },
    children,
  )
}

export function useModelSelection(): ModelSelectionContextValue {
  const ctx = useContext(ModelSelectionContext)
  if (!ctx) {
    throw new Error('useModelSelection must be used within a ModelSelectionProvider')
  }
  return ctx
}

