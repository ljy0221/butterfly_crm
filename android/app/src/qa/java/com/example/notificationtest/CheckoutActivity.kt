package com.example.notificationtest

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.notificationtest.butterfly.EventTrigger
import com.example.notificationtest.cart.CartStore

/**
 * QA 빌드 전용 — 결제 시뮬레이션.
 * [결제 확정] 탭 시 purchase 이벤트 발사 + CartStore 비우기.
 */
class CheckoutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val items = CartStore.current
        val total = CartStore.totalPrice()
        findViewById<TextView>(R.id.tvSummary).text =
            "총 ${items.size}종 / ${CartStore.totalCount()}개\n결제금액: ${"%,d".format(total)}원"

        findViewById<TextView>(R.id.btnConfirm).setOnClickListener {
            if (items.isEmpty()) {
                Toast.makeText(this, "장바구니가 비어있습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val orderId = "O-${System.currentTimeMillis()}"
            val payload: List<Map<String, Any>> = items.map { item ->
                mapOf(
                    "productId" to item.product.id,
                    "category" to item.product.category,
                    "quantity" to item.quantity,
                    "price" to (item.product.discountedPrice ?: item.product.price)
                )
            }
            EventTrigger.firePurchaseSilently(this, orderId, payload, total)
            Toast.makeText(this, "결제 완료 — purchase 이벤트 발사", Toast.LENGTH_LONG).show()
            CartStore.clear()
            finish()
        }
    }
}
