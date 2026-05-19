importScripts('https://www.gstatic.com/firebasejs/12.13.0/firebase-app-compat.js')
importScripts('https://www.gstatic.com/firebasejs/12.13.0/firebase-messaging-compat.js')

function decodeFirebaseConfig() {
  const encoded = new URL(self.location.href).searchParams.get('firebaseConfig')
  if (!encoded) return null

  try {
    return JSON.parse(atob(encoded))
  } catch (err) {
    console.warn('[FirebaseMessagingSW] Firebase 설정 파싱 실패:', err)
    return null
  }
}

function toNotification(payload) {
  const data = payload?.data || {}
  const notification = payload?.notification || {}

  return {
    title: notification.title || data.title || '알림',
    options: {
      body: notification.body || data.body || '',
      data: {
        clickUrl: payload?.fcmOptions?.link || data.clickUrl || data.url || '/',
      },
    },
  }
}

const firebaseConfig = decodeFirebaseConfig()

if (firebaseConfig) {
  firebase.initializeApp(firebaseConfig)

  const messaging = firebase.messaging()
  messaging.onBackgroundMessage((payload) => {
    const { title, options } = toNotification(payload)
    return self.registration.showNotification(title, options)
  })
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close()

  const clickUrl = event.notification.data?.clickUrl || '/'
  const targetUrl = new URL(clickUrl, self.location.origin).href

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
      for (const client of clients) {
        if (client.url === targetUrl && 'focus' in client) {
          return client.focus()
        }
      }

      if (self.clients.openWindow) {
        return self.clients.openWindow(targetUrl)
      }

      return undefined
    }),
  )
})
