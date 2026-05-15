package com.example.notificationtest

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationtest.butterfly.EventTrigger
import com.example.notificationtest.catalog.ProductCatalog

/**
 * QA 빌드 전용 — 카테고리별 상품 그리드.
 * Intent extra "category" 로 ProductCatalog 의 해당 카테고리 상품 표시.
 */
class CategoryListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_list)

        val categoryName = intent.getStringExtra("category") ?: run { finish(); return }
        findViewById<TextView>(R.id.tvTitle).text = categoryName

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val products = ProductCatalog.productsByCategory(categoryName)
        val rv = findViewById<RecyclerView>(R.id.rvProducts)
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = Adapter(products) { product ->
            EventTrigger.fireProductClickSilently(
                this, product.category, product.id,
                product.discountedPrice ?: product.price
            )
            startActivity(
                Intent(this, ProductDetailActivity::class.java)
                    .putExtra("productId", product.id)
            )
        }
    }

    private class Adapter(
        val items: List<ProductCatalog.Product>,
        val onClick: (ProductCatalog.Product) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgProduct)
            val category: TextView = v.findViewById(R.id.tvCategory)
            val name: TextView = v.findViewById(R.id.tvName)
            val price: TextView = v.findViewById(R.id.tvPrice)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_product_card, parent, false)
        )
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = items[position]
            holder.img.setImageResource(p.imageRes)
            holder.category.text = p.category
            holder.name.text = p.name
            holder.price.text = "${"%,d".format(p.discountedPrice ?: p.price)}원"
            holder.itemView.setOnClickListener { onClick(p) }
        }
    }
}
