import { useState, useCallback, useEffect } from 'react'

export interface ToastMessage {
  id: string
  title: string
  body: string
  clickUrl?: string
}

let toastListeners: Array<(msg: ToastMessage) => void> = []

export function showToast(msg: Omit<ToastMessage, 'id'>) {
  const toast: ToastMessage = { ...msg, id: Date.now().toString() }
  toastListeners.forEach((fn) => fn(toast))
}

export function useToastStore() {
  const [toasts, setToasts] = useState<ToastMessage[]>([])

  const subscribe = useCallback(() => {
    const listener = (msg: ToastMessage) => {
      setToasts((prev) => [...prev, msg])
      setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== msg.id))
      }, 5000)
    }
    toastListeners.push(listener)
    return () => {
      toastListeners = toastListeners.filter((fn) => fn !== listener)
    }
  }, [])

  return { toasts, subscribe }
}
