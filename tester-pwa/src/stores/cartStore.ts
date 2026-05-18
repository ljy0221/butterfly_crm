import { useState, useCallback, useEffect } from 'react'
import type { Product } from '../data/catalog'

export interface CartItem {
  product: Product
  quantity: number
}

let cartItems: CartItem[] = []
let listeners: Array<() => void> = []

function notify() {
  listeners.forEach((fn) => fn())
}

export const cartStore = {
  getItems: () => cartItems,
  addItem(product: Product, quantity = 1) {
    const existing = cartItems.find((i) => i.product.id === product.id)
    if (existing) {
      cartItems = cartItems.map((i) =>
        i.product.id === product.id ? { ...i, quantity: i.quantity + quantity } : i
      )
    } else {
      cartItems = [...cartItems, { product, quantity }]
    }
    notify()
  },
  removeItem(productId: string) {
    cartItems = cartItems.filter((i) => i.product.id !== productId)
    notify()
  },
  updateQuantity(productId: string, quantity: number) {
    if (quantity <= 0) {
      cartStore.removeItem(productId)
      return
    }
    cartItems = cartItems.map((i) =>
      i.product.id === productId ? { ...i, quantity } : i
    )
    notify()
  },
  clear() {
    cartItems = []
    notify()
  },
  getTotalPrice() {
    return cartItems.reduce((sum, i) => {
      const price = i.product.discountedPrice ?? i.product.price
      return sum + price * i.quantity
    }, 0)
  },
  getTotalCount() {
    return cartItems.reduce((sum, i) => sum + i.quantity, 0)
  },
  subscribe(fn: () => void) {
    listeners.push(fn)
    return () => {
      listeners = listeners.filter((l) => l !== fn)
    }
  },
}

export function useCart() {
  const [, forceUpdate] = useState(0)
  const refresh = useCallback(() => forceUpdate((n) => n + 1), [])

  useEffect(() => {
    return cartStore.subscribe(refresh)
  }, [refresh])

  return {
    items: cartStore.getItems(),
    addItem: cartStore.addItem.bind(cartStore),
    removeItem: cartStore.removeItem.bind(cartStore),
    updateQuantity: cartStore.updateQuantity.bind(cartStore),
    clear: cartStore.clear.bind(cartStore),
    totalPrice: cartStore.getTotalPrice(),
    totalCount: cartStore.getTotalCount(),
  }
}
