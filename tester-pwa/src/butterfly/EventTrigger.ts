import apiClient from '../api/client'
import { getOrCreateDeviceId } from '../device/DeviceManager'

const USER_ID = import.meta.env.VITE_USER_ID || 'user-142'

const DEMO_PRODUCT_ID = 'sku-9847'
const DEMO_PRODUCT_NAME = '데일리 러닝화'
const DEMO_PRODUCT_CATEGORY = '운동화'
const DEMO_PRODUCT_PRICE = 89000

function nowIsoLocal(): string {
  return new Date().toLocaleString('sv-SE', { timeZone: 'Asia/Seoul' }).replace(' ', 'T')
}

async function sendEvent(eventCategory: string, eventData: Record<string, unknown>): Promise<void> {
  const deviceId = getOrCreateDeviceId()
  try {
    await apiClient.post('/api/v1/events', {
      deviceId,
      userId: USER_ID,
      eventCategory,
      occurredAt: nowIsoLocal(),
      eventData,
    })
    console.log(`[EventTrigger] ${eventCategory} OK`)
  } catch (err) {
    console.warn(`[EventTrigger] ${eventCategory} FAIL`, err)
  }
}

async function sendEventSilently(eventCategory: string, eventData: Record<string, unknown>): Promise<void> {
  const deviceId = getOrCreateDeviceId()
  try {
    await apiClient.post('/api/v1/events', {
      deviceId,
      userId: USER_ID,
      eventCategory,
      occurredAt: nowIsoLocal(),
      eventData,
    })
  } catch {
    // silent
  }
}

function productViewPayload() {
  return {
    category: DEMO_PRODUCT_CATEGORY,
    productId: DEMO_PRODUCT_ID,
    productName: DEMO_PRODUCT_NAME,
    price: DEMO_PRODUCT_PRICE,
  }
}

export const EventTrigger = {
  fireAppOpen: () => sendEvent('app_open', { appVersion: '1.0.0' }),
  fireProductView: () => sendEvent('product_view', productViewPayload()),
  fireProductViewSilently: () => sendEventSilently('product_view', productViewPayload()),
  firePurchase: () => {
    const orderId = `O-${Date.now()}`
    return sendEvent('purchase', { orderId, productId: DEMO_PRODUCT_ID, totalAmount: DEMO_PRODUCT_PRICE })
  },
  fireAppOpenSilently: () => sendEventSilently('app_open', { appVersion: '1.0.0' }),
  fireSearchSilently: (query: string, resultCount: number) =>
    sendEventSilently('search', { query, resultCount }),
  fireCategoryViewSilently: (category: string) =>
    sendEventSilently('category_view', { category }),
  fireProductClickSilently: (category: string, productId: string, price: number) =>
    sendEventSilently('product_click', { category, productId, price }),
  fireProductViewWithDataSilently: (
    category: string, productId: string, productName: string, price: number
  ) => sendEventSilently('product_view', { category, productId, productName, price }),
  fireAddToCartSilently: (category: string, productId: string, quantity: number, price: number) =>
    sendEventSilently('add_to_cart', { category, productId, quantity, price }),
  fireCartViewSilently: (itemCount: number, totalPrice: number) =>
    sendEventSilently('cart_view', { itemCount, totalPrice }),
  firePurchaseSilently: (
    orderId: string, items: Array<Record<string, unknown>>, totalAmount: number
  ) => sendEventSilently('purchase', { orderId, items, totalAmount }),
  fireWishlistAddSilently: (category: string, productId: string) =>
    sendEventSilently('wishlist_add', { category, productId }),
  fireLoginSilently: (userId: string) =>
    sendEventSilently('login', { userId }),
}
