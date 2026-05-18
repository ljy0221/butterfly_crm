import apiClient from './client'
import { showToast } from '../stores/toastStore'
import { getDeviceInternalId } from '../device/DeviceManager'

let pollingInterval: ReturnType<typeof setInterval> | null = null

export function startNotificationPolling() {
  if (pollingInterval) return

  pollingInterval = setInterval(async () => {
    const internalId = getDeviceInternalId()
    if (!internalId) return

    try {
      const res = await apiClient.get(
        `/api/v1/devices/${internalId}/pending-notifications`
      )
      const notifications: Array<{ title?: string; body?: string; clickUrl?: string }> =
        res.data?.data ?? []
      for (const n of notifications) {
        showToast({
          title: n.title ?? '알림',
          body: n.body ?? '',
          clickUrl: n.clickUrl,
        })
      }
    } catch {
      // 엔드포인트 없거나 미등록 시 무시
    }
  }, 10000)
}

export function stopNotificationPolling() {
  if (pollingInterval) {
    clearInterval(pollingInterval)
    pollingInterval = null
  }
}
