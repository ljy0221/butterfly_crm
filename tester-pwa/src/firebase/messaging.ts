import { initializeApp, getApps, type FirebaseOptions } from 'firebase/app'
import {
  getMessaging,
  getToken,
  isSupported,
  onMessage,
  type MessagePayload,
  type Messaging,
} from 'firebase/messaging'

import { storePushToken } from '../device/DeviceManager'

const FIREBASE_SW_SCOPE = '/firebase-cloud-messaging-push-scope'
const FIREBASE_SW_PATH = '/firebase-messaging-sw.js'

export interface NotificationPayload {
  title: string
  body: string
  clickUrl?: string
}

function getFirebaseConfig(): FirebaseOptions | null {
  const config = {
    apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
    authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
    projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
    storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
    messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
    appId: import.meta.env.VITE_FIREBASE_APP_ID,
  }

  if (!config.apiKey || !config.projectId || !config.messagingSenderId || !config.appId) {
    return null
  }

  return config
}

function getVapidKey(): string {
  return import.meta.env.VITE_FIREBASE_VAPID_KEY ?? ''
}

export function isBackgroundNotificationConfigured(): boolean {
  return Boolean(getFirebaseConfig() && getVapidKey())
}

async function getMessagingInstance(): Promise<Messaging | null> {
  const config = getFirebaseConfig()
  if (!config || !getVapidKey()) return null

  if (!(await isSupported())) {
    console.warn('[FirebaseMessaging] 이 브라우저는 Web Push를 지원하지 않습니다.')
    return null
  }

  const app = getApps().length > 0 ? getApps()[0] : initializeApp(config)
  return getMessaging(app)
}

function encodeConfig(config: FirebaseOptions): string {
  return btoa(JSON.stringify(config))
}

async function getFirebaseServiceWorkerRegistration(): Promise<ServiceWorkerRegistration> {
  const config = getFirebaseConfig()
  if (!config) {
    throw new Error('Firebase 설정이 없습니다.')
  }
  if (!('serviceWorker' in navigator)) {
    throw new Error('Service Worker를 지원하지 않는 브라우저입니다.')
  }

  const swUrl = `${FIREBASE_SW_PATH}?firebaseConfig=${encodeURIComponent(encodeConfig(config))}`
  return navigator.serviceWorker.register(swUrl, { scope: FIREBASE_SW_SCOPE })
}

function toNotificationPayload(payload: MessagePayload): NotificationPayload {
  return {
    title: payload.notification?.title ?? payload.data?.title ?? '알림',
    body: payload.notification?.body ?? payload.data?.body ?? '',
    clickUrl: payload.fcmOptions?.link ?? payload.data?.clickUrl ?? payload.data?.url,
  }
}

export async function refreshBackgroundNotificationToken(): Promise<string | null> {
  if (!isBackgroundNotificationConfigured()) return null
  if (!('Notification' in window) || Notification.permission !== 'granted') return null

  const messaging = await getMessagingInstance()
  if (!messaging) return null

  const serviceWorkerRegistration = await getFirebaseServiceWorkerRegistration()
  const token = await getToken(messaging, {
    vapidKey: getVapidKey(),
    serviceWorkerRegistration,
  })

  if (token) {
    storePushToken(token)
    return token
  }

  return null
}

export async function enableBackgroundNotifications(): Promise<string> {
  if (!isBackgroundNotificationConfigured()) {
    throw new Error('Firebase Web Push 환경변수가 설정되지 않았습니다.')
  }
  if (!('Notification' in window)) {
    throw new Error('이 브라우저는 알림 권한을 지원하지 않습니다.')
  }

  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    throw new Error('알림 권한이 허용되지 않았습니다.')
  }

  const token = await refreshBackgroundNotificationToken()
  if (!token) {
    throw new Error('FCM 토큰 발급에 실패했습니다.')
  }

  return token
}

export function subscribeForegroundMessages(
  handler: (payload: NotificationPayload) => void,
): () => void {
  let disposed = false
  let unsubscribe: (() => void) | undefined

  getMessagingInstance()
    .then((messaging) => {
      if (!messaging || disposed) return

      unsubscribe = onMessage(messaging, (payload) => {
        handler(toNotificationPayload(payload))
      })
    })
    .catch((err) => {
      console.warn('[FirebaseMessaging] foreground 메시지 구독 실패:', err)
    })

  return () => {
    disposed = true
    unsubscribe?.()
  }
}
