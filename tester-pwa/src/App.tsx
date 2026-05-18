import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useEffect } from 'react'
import { registerDevice } from './device/DeviceManager'
import { startNotificationPolling } from './api/notificationPoller'
import Toast from './components/Toast'
import HomePage from './pages/HomePage'
import CategoryListPage from './pages/CategoryListPage'
import ProductDetailPage from './pages/ProductDetailPage'
import CartPage from './pages/CartPage'
import CheckoutPage from './pages/CheckoutPage'

function App() {
  useEffect(() => {
    registerDevice()
    startNotificationPolling()
  }, [])

  return (
    <BrowserRouter>
      <Toast />
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
