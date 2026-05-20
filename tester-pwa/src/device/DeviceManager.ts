import { v4 as uuidv4 } from 'uuid'
import { initializeApp, getApps } from 'firebase/app'
import { getMessaging, getToken, isSupported } from 'firebase/messaging'
import apiClient from '../api/client'

const DEVICE_ID_KEY = 'butterfly_device_id'
const DEVICE_INTERNAL_ID_KEY = 'butterfly_device_internal_id'
const DEVICE_REGISTERED_KEY = 'butterfly_device_registered'

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
}

function getFirebaseApp() {
  return getApps().length ? getApps()[0] : initializeApp(firebaseConfig)
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

async function getFcmToken(): Promise<string> {
  const supported = await isSupported()
  if (!supported) {
    console.warn('[DeviceManager] FCM not supported in this browser')
    return 'no_token'
  }

  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    console.warn('[DeviceManager] Notification permission denied')
    return 'no_token'
  }

  const app = getFirebaseApp()
  const messaging = getMessaging(app)

  const swReg = await navigator.serviceWorker.register('/firebase-messaging-sw.js', {
    scope: '/',
  })

  const token = await getToken(messaging, {
    vapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY,
    serviceWorkerRegistration: swReg,
  })

  console.log('[DeviceManager] FCM token:', token)
  return token
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

  const pushToken = await getFcmToken()

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
    sessionStorage.setItem(DEVICE_REGISTERED_KEY, deviceId)
    console.log('[DeviceManager] Registered:', deviceId, 'internalId:', internalId)
  } catch (err) {
    console.warn('[DeviceManager] Registration failed (non-fatal):', err)
  }
}
