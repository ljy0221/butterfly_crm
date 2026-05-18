import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useToastStore } from '../stores/toastStore'

export default function Toast() {
  const { toasts, subscribe } = useToastStore()
  const navigate = useNavigate()

  useEffect(() => {
    return subscribe()
  }, [subscribe])

  if (toasts.length === 0) return null

  return (
    <div style={{
      position: 'fixed',
      top: 16,
      left: '50%',
      transform: 'translateX(-50%)',
      zIndex: 9999,
      display: 'flex',
      flexDirection: 'column',
      gap: 8,
      width: '90%',
      maxWidth: 400,
    }}>
      {toasts.map((toast) => (
        <div
          key={toast.id}
          onClick={() => toast.clickUrl && navigate(toast.clickUrl)}
          style={{
            background: '#6750A4',
            color: '#fff',
            borderRadius: 12,
            padding: '12px 16px',
            boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
            cursor: toast.clickUrl ? 'pointer' : 'default',
            animation: 'slideIn 0.3s ease',
          }}
        >
          <div style={{ fontWeight: 700, marginBottom: 4 }}>{toast.title}</div>
          <div style={{ fontSize: 14, opacity: 0.9 }}>{toast.body}</div>
        </div>
      ))}
    </div>
  )
}
