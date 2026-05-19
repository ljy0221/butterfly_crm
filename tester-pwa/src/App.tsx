import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useEffect } from 'react'
import { registerDevice } from './device/DeviceManager'
import { startNotificationPolling, stopNotificationPolling } from './api/notificationPoller'
import {
  refreshBackgroundNotificationToken,
  subscribeForegroundMessages,
} from './firebase/messaging'
import { showToast } from './stores/toastStore'
import Toast from './components/Toast'
import NotificationPermissionButton from './components/NotificationPermissionButton'
import HomePage from './pages/HomePage'
import CategoryListPage from './pages/CategoryListPage'
import ProductDetailPage from './pages/ProductDetailPage'
import CartPage from './pages/CartPage'
import CheckoutPage from './pages/CheckoutPage'

function App() {
  useEffect(() => {
    registerDevice()

    const unsubscribeForegroundMessages = subscribeForegroundMessages((notification) => {
      showToast(notification)
    })

    refreshBackgroundNotificationToken()
      .then(async (pushToken) => {
        if (pushToken) {
          await registerDevice({ pushToken, force: true })
          stopNotificationPolling()
          return
        }

        startNotificationPolling()
      })
      .catch((err) => {
        console.warn('[App] FCM 토큰 갱신 실패, polling fallback 사용:', err)
        startNotificationPolling()
      })

    return () => {
      unsubscribeForegroundMessages()
      stopNotificationPolling()
    }
  }, [])

  return (
    <BrowserRouter>
      <Toast />
      <NotificationPermissionButton />
      <Routes>
        <Route path="/" element={<Navigate to="/home" replace />} />
        <Route path="/home" element={<HomePage />} />
        <Route path="/category/:categoryId" element={<CategoryListPage />} />
        <Route path="/product/:productId" element={<ProductDetailPage />} />
        <Route path="/cart" element={<CartPage />} />
        <Route path="/checkout" element={<CheckoutPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
