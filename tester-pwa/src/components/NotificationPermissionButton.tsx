import { useEffect, useState } from 'react'

import { registerDevice } from '../device/DeviceManager'
import {
  enableBackgroundNotifications,
  isBackgroundNotificationConfigured,
} from '../firebase/messaging'
import { stopNotificationPolling } from '../api/notificationPoller'
import { showToast } from '../stores/toastStore'

type PermissionState = 'unsupported' | 'hidden' | 'ready' | 'working'

function getInitialState(): PermissionState {
  if (!isBackgroundNotificationConfigured() || !('Notification' in window)) {
    return 'unsupported'
  }
  return Notification.permission === 'granted' ? 'hidden' : 'ready'
}

export default function NotificationPermissionButton() {
  const [state, setState] = useState<PermissionState>(getInitialState)

  useEffect(() => {
    setState(getInitialState())
  }, [])

  if (state !== 'ready' && state !== 'working') return null

  const handleClick = async () => {
    setState('working')
    try {
      const pushToken = await enableBackgroundNotifications()
      await registerDevice({ pushToken, force: true })
      stopNotificationPolling()
      showToast({
        title: '알림 설정 완료',
        body: '앱이 닫혀 있어도 브라우저 알림을 받을 수 있습니다.',
      })
      setState('hidden')
    } catch (err) {
      console.warn('[NotificationPermissionButton] 알림 설정 실패:', err)
      showToast({
        title: '알림 설정 실패',
        body: err instanceof Error ? err.message : '알림 권한을 다시 확인해주세요.',
      })
      setState('ready')
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={state === 'working'}
      style={{
        position: 'fixed',
        right: 16,
        bottom: 16,
        zIndex: 9998,
        borderRadius: 999,
        border: 0,
        background: '#1f2937',
        color: '#fff',
        padding: '10px 14px',
        fontSize: 13,
        fontWeight: 700,
        boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
      }}
    >
      {state === 'working' ? '알림 설정 중...' : '알림 허용'}
    </button>
  )
}
