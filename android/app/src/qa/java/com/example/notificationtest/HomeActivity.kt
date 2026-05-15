package com.example.notificationtest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationtest.butterfly.EventTrigger
import com.example.notificationtest.catalog.ProductCatalog
import com.google.firebase.messaging.FirebaseMessaging

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "QA_Home"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 1) 디바이스 등록 + app_open (silent, savedInstanceState 가드로 rotation 중복 방지)
        if (savedInstanceState == null) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result.orEmpty()
                    if (token.isNotEmpty()) {
                        EventTrigger.registerDeviceSilently(this, token)
                    } else {
                        Log.w(TAG, "FCM token empty, skipping silent register")
                    }
                } else {
                    Log.w(TAG, "FCM token failed, skipping silent register", task.exception)
                }
            }
            EventTrigger.fireAppOpenSilently(this)
        }

        // 2) 카테고리 그리드
        val rvCat = findViewById<RecyclerView>(R.id.rvCategories)
        rvCat.layoutManager = GridLayoutManager(this, 4)
        rvCat.adapter = CategoryAdapter(ProductCatalog.categories) { category ->
            EventTrigger.fireCategoryViewSilently(this, category.name)
            startActivity(
                Intent(this, CategoryListActivity::class.java)
                    .putExtra("category", category.name)
            )
        }

        // 3) 인기 상품 (horizontal)
        val rvPop = findViewById<RecyclerView>(R.id.rvPopular)
        rvPop.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvPop.adapter = ProductAdapter(ProductCatalog.popularProducts()) { product ->
            EventTrigger.fireProductClickSilently(
                this, product.category, product.id,
                product.discountedPrice ?: product.price
            )
            startActivity(
                Intent(this, ProductDetailActivity::class.java)
                    .putExtra("productId", product.id)
            )
        }

        // 4) 검색
        val etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = etSearch.text.toString().trim()
                if (q.isNotEmpty()) {
                    val results = ProductCatalog.search(q)
                    EventTrigger.fireSearchSilently(this, q, results.size)
                    Log.d(TAG, "search '$q' → ${results.size} results")
                }
                true
            } else false
        }

        // 5) 장바구니 진입 — Task 9에서 CartActivity 추가 후 wiring (Task 10 에서 enable)
        findViewById<TextView>(R.id.btnCart).setOnClickListener {
            Log.d(TAG, "Cart button — CartActivity wiring pending (Task 9/10)")
        }

        // 6) Debug 진입
        findViewById<TextView>(R.id.btnDebug).setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }
    }

    private class CategoryAdapter(
        val items: List<ProductCatalog.Category>,
        val onClick: (ProductCatalog.Category) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: TextView = v.findViewById(R.id.tvIcon)
            val name: TextView = v.findViewById(R.id.tvName)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_grid, parent, false)
        )
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            holder.icon.text = c.icon
            holder.name.text = c.name
            holder.itemView.setOnClickListener { onClick(c) }
        }
    }

    private class ProductAdapter(
        val items: List<ProductCatalog.Product>,
        val onClick: (ProductCatalog.Product) -> Unit
    ) : RecyclerView.Adapter<ProductAdapter.VH>() {
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
