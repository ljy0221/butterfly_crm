importScripts('https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: 'AIzaSyBub-7p7HIsy9UgLjL9GBQ91CUn1pMJf0E',
  authDomain: 'butterfly-test-ce57c.firebaseapp.com',
  projectId: 'butterfly-test-ce57c',
  storageBucket: 'butterfly-test-ce57c.firebasestorage.app',
  messagingSenderId: '376141712101',
  appId: '1:376141712101:web:8c515d53b8927309f4e20c',
});

const messaging = firebase.messaging();

// 백그라운드 메시지 처리
messaging.onBackgroundMessage((payload) => {
  const { title, body } = payload.notification ?? {};
  self.registration.showNotification(title ?? 'Butterfly', {
    body: body ?? '',
    icon: '/pwa-192x192.png',
  });
});
