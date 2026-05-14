package com.example.notificationtest.cart

import com.example.notificationtest.catalog.ProductCatalog.Product

/**
 * QA 빌드 전용 — 메모리 장바구니. 앱 재시작 시 초기화.
 * 시연/QA 시나리오에서 짧은 흐름을 검증하므로 영속화는 불필요.
 */
object CartStore {

    data class CartItem(val product: Product, val quantity: Int)

    private val items = mutableListOf<CartItem>()

    val current: List<CartItem> get() = items.toList()

    fun add(product: Product, quantity: Int = 1) {
        val existing = items.indexOfFirst { it.product.id == product.id }
        if (existing >= 0) {
            items[existing] = items[existing].copy(quantity = items[existing].quantity + quantity)
        } else {
            items += CartItem(product, quantity)
        }
    }

    fun remove(productId: String) {
        items.removeAll { it.product.id == productId }
    }

    fun clear() {
        items.clear()
    }

    fun totalCount(): Int = items.sumOf { it.quantity }

    fun totalPrice(): Int = items.sumOf {
        (it.product.discountedPrice ?: it.product.price) * it.quantity
    }
}
