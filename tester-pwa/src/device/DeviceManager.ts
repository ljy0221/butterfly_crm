import { v4 as uuidv4 } from 'uuid'
import apiClient from '../api/client'

const DEVICE_ID_KEY = 'butterfly_device_id'
const DEVICE_INTERNAL_ID_KEY = 'butterfly_device_internal_id'
const DEVICE_REGISTERED_KEY = 'butterfly_device_registered'

export function getOrCreateDeviceId(): string {
  try {
    let id = localStorage.getItem(DEVICE_ID_KEY)
    if (!id) {
      id = 'dev_' + uuidv4().replace(/-/g, '').slice(0, 16)
      localStorage.setItem(DEVICE_ID_KEY, id)
    }
    return id
  } catch {
    return 'dev_' + uuidv4().replace(/-/g, '').slice(0, 16)
  }
}

export function getDeviceInternalId(): string | null {
  try {
    return localStorage.getItem(DEVICE_INTERNAL_ID_KEY)
  } catch {
    return null
  }
}

export async function registerDevice(): Promise<void> {
  const deviceId = getOrCreateDeviceId()
  const userId = import.meta.env.VITE_USER_ID || 'user-142'
  const projectId = import.meta.env.VITE_PROJECT_ID

  if (!projectId) {
    console.warn('[DeviceManager] VITE_PROJECT_ID not set, skipping registration')
    return
  }

  if (sessionStorage.getItem(DEVICE_REGISTERED_KEY) === deviceId) {
    return
  }

  try {
    const res = await apiClient.post('/api/v1/devices', {
      deviceId,
      userId,
      platform: 'WEB',
      pushToken: 'no_token',
      appVersion: '1.0.0',
      osVersion: navigator.userAgent.slice(0, 50),
    })
    const internalId = res.data?.data?.internalId
    if (internalId) {
      localStorage.setItem(DEVICE_INTERNAL_ID_KEY, String(internalId))
    }
    sessionStorage.setItem(DEVICE_REGISTERED_KEY, deviceId)
    console.log('[DeviceManager] Registered:', deviceId, 'internalId:', internalId)
  } catch (err) {
    console.warn('[DeviceManager] Registration failed (non-fatal):', err)
  }
}
