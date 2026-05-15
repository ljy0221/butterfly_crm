package com.example.notificationtest

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationtest.butterfly.EventTrigger
import com.example.notificationtest.cart.CartStore

/**
 * QA 빌드 전용 — 장바구니 화면.
 * onCreate 첫 진입 시 cart_view 이벤트 발사 (rotation 시 중복 방지).
 */
class CartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        if (savedInstanceState == null) {
            EventTrigger.fireCartViewSilently(this, CartStore.totalCount(), CartStore.totalPrice())
        }

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val items = CartStore.current
        val rv = findViewById<RecyclerView>(R.id.rvCartItems)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        if (items.isEmpty()) {
            rv.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
        } else {
            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = Adapter(items) { item ->
                CartStore.remove(item.product.id)
                recreate()
            }
        }

        findViewById<TextView>(R.id.tvTotal).text =
            "총 ${CartStore.totalCount()}개 / ${"%,d".format(CartStore.totalPrice())}원"

        findViewById<TextView>(R.id.btnCheckout).setOnClickListener {
            if (CartStore.current.isEmpty()) return@setOnClickListener
            startActivity(Intent(this, CheckoutActivity::class.java))
        }
    }

    private class Adapter(
        val items: List<CartStore.CartItem>,
        val onRemove: (CartStore.CartItem) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgProduct)
            val name: TextView = v.findViewById(R.id.tvName)
            val detail: TextView = v.findViewById(R.id.tvDetail)
            val remove: TextView = v.findViewById(R.id.btnRemove)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_cart_row, parent, false)
        )
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.img.setImageResource(item.product.imageRes)
            holder.name.text = item.product.name
            holder.detail.text = "${item.product.category} · ${item.quantity}개 · " +
                "${"%,d".format(item.product.discountedPrice ?: item.product.price)}원"
            holder.remove.setOnClickListener { onRemove(item) }
        }
    }
}
