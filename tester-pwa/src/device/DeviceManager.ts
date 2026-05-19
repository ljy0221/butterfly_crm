import { v4 as uuidv4 } from 'uuid'
import apiClient from '../api/client'

const DEVICE_ID_KEY = 'butterfly_device_id'
const DEVICE_INTERNAL_ID_KEY = 'butterfly_device_internal_id'
const DEVICE_REGISTERED_KEY = 'butterfly_device_registered'
const DEVICE_PUSH_TOKEN_KEY = 'butterfly_device_push_token'

interface RegisterDeviceOptions {
  pushToken?: string
  force?: boolean
}

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

export function getStoredPushToken(): string {
  try {
    return localStorage.getItem(DEVICE_PUSH_TOKEN_KEY) ?? ''
  } catch {
    return ''
  }
}

export function storePushToken(pushToken: string): void {
  try {
    if (pushToken) {
      localStorage.setItem(DEVICE_PUSH_TOKEN_KEY, pushToken)
    } else {
      localStorage.removeItem(DEVICE_PUSH_TOKEN_KEY)
    }
  } catch {
    // localStorage 접근이 막힌 환경에서는 현재 세션 등록만 시도한다.
  }
}

export async function registerDevice(options: RegisterDeviceOptions = {}): Promise<void> {
  const deviceId = getOrCreateDeviceId()
  const userId = import.meta.env.VITE_USER_ID || 'user-142'
  const projectId = import.meta.env.VITE_PROJECT_ID
  const pushToken = options.pushToken ?? getStoredPushToken()

  if (!projectId) {
    console.warn('[DeviceManager] VITE_PROJECT_ID not set, skipping registration')
    return
  }

  const registrationSignature = `${deviceId}:${pushToken || 'no_push_token'}`
  if (!options.force && sessionStorage.getItem(DEVICE_REGISTERED_KEY) === registrationSignature) {
    return
  }

  try {
    const res = await apiClient.post('/api/v1/devices', {
      deviceId,
      userId,
      platform: 'WEB',
      pushToken,
      appVersion: '1.0.0',
      osVersion: navigator.userAgent.slice(0, 50),
    })
    const internalId = res.data?.data?.internalId
    if (internalId) {
      localStorage.setItem(DEVICE_INTERNAL_ID_KEY, String(internalId))
    }
    sessionStorage.setItem(DEVICE_REGISTERED_KEY, registrationSignature)
    console.log('[DeviceManager] Registered:', deviceId, 'internalId:', internalId)
  } catch (err) {
    console.warn('[DeviceManager] Registration failed (non-fatal):', err)
  }
}
