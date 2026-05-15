package com.example.notificationtest

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.notificationtest.butterfly.EventTrigger
import com.google.firebase.messaging.FirebaseMessaging

/**
 * 5/22 시연용 — 박준호 페르소나 상품 상세 페이지.
 *
 * onCreate 에서:
 *  1) 백그라운드 디바이스 등록 (silent — UI 피드백 없음)
 *  2) 첫 진입 product_view 이벤트 자동 발사
 *  3) [+1 view] 디버그 핸들 binding
 *  4) [⚙] Debug 아이콘 → DebugActivity 이동
 */
class ProductDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ProductDetail"
        private const val NOTIFICATION_PERMISSION_CODE = 100
        private const val CHANNEL_ID = "fcm_default_channel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_detail)

        createNotificationChannel()
        requestNotificationPermission()

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result.orEmpty()
                if (token.isNotEmpty()) {
                    Log.d(TAG, "FCM token (silent register): ${token.take(20)}...")
                    EventTrigger.registerDeviceSilently(this, token)
                } else {
                    Log.w(TAG, "FCM token empty, skipping silent register")
                }
            } else {
                Log.w(TAG, "FCM token failed, skipping silent register", task.exception)
            }
        }

        // QA 빌드: Intent extra "productId" 로 동적 상품 표시 + 그 상품 정보로 product_view 발사
        // demo 빌드: 기존 단일 상품 (DEMO_PRODUCT_*) 그대로 사용
        val productId = intent.getStringExtra("productId")

        // savedInstanceState == null 일 때만 — rotation/recreate 시 중복 발사 방지
        if (savedInstanceState == null) {
            if (BuildConfig.IS_QA_BUILD && productId != null) {
                invokeQaProductView(productId)
            } else {
                EventTrigger.fireProductViewSilently(this)
            }
        }

        findViewById<TextView>(R.id.tvOriginalPrice).apply {
            paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        findViewById<TextView>(R.id.tvPlusOneView).setOnClickListener {
            EventTrigger.fireProductViewSilently(this)
            Log.d(TAG, "[+1 view] tapped — product_view fired")
        }

        findViewById<TextView>(R.id.btnDebug).setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }

        // 장바구니 CTA — qa 빌드는 실제 CartStore add + add_to_cart 이벤트, demo 빌드는 토스트만
        findViewById<TextView>(R.id.btnAddToCart).setOnClickListener {
            if (BuildConfig.IS_QA_BUILD) {
                invokeQaAddToCart(productId)
            } else {
                android.widget.Toast.makeText(this, "장바구니 (시연용 토스트)", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.btnBuyNow).setOnClickListener {
            android.widget.Toast.makeText(this, "바로구매 (시뮬레이션)", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "FCM 알림", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "FCM 푸시 알림 채널" }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * QA 빌드 전용 — 장바구니 추가. ProductCatalog lookup + CartStore.add + add_to_cart 이벤트.
     * 실패 시 토스트로 알림 (시연 무관).
     */
    private fun invokeQaAddToCart(productId: String?) {
        if (productId == null) {
            android.widget.Toast.makeText(this, "productId 없음 — 장바구니 담기 불가", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val catalogClass = Class.forName("com.example.notificationtest.catalog.ProductCatalog")
            val findByIdMethod = catalogClass.getMethod("findById", String::class.java)
            val catalogInstance = catalogClass.getField("INSTANCE").get(null)
            val product = findByIdMethod.invoke(catalogInstance, productId) ?: return

            val storeClass = Class.forName("com.example.notificationtest.cart.CartStore")
            val productClass = Class.forName("com.example.notificationtest.catalog.ProductCatalog\$Product")
            val addMethod = storeClass.getMethod("add", productClass, Int::class.javaPrimitiveType)
            val storeInstance = storeClass.getField("INSTANCE").get(null)
            addMethod.invoke(storeInstance, product, 1)

            val category = product.javaClass.getMethod("getCategory").invoke(product) as String
            val price = product.javaClass.getMethod("getPrice").invoke(product) as Int

            EventTrigger.fireAddToCartSilently(this, category, productId, 1, price)
            android.widget.Toast.makeText(this, "장바구니에 담겼습니다", android.widget.Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.w(TAG, "QA add-to-cart failed", t)
            android.widget.Toast.makeText(this, "장바구니 담기 실패: ${t.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /**
     * QA 빌드 전용 — ProductCatalog 에서 productId 로 lookup 후 product_view 이벤트 발사.
     * main source 에서 ProductCatalog 를 직접 import 할 수 없어 reflection.
     * lookup 실패 시 demo fallback (fireProductViewSilently).
     */
    private fun invokeQaProductView(productId: String) {
        try {
            val catalogClass = Class.forName("com.example.notificationtest.catalog.ProductCatalog")
            val findByIdMethod = catalogClass.getMethod("findById", String::class.java)
            val instance = catalogClass.getField("INSTANCE").get(null)
            val product = findByIdMethod.invoke(instance, productId) ?: run {
                Log.w(TAG, "Product not found: $productId — fallback to default product_view")
                EventTrigger.fireProductViewSilently(this)
                return
            }
            val category = product.javaClass.getMethod("getCategory").invoke(product) as String
            val name = product.javaClass.getMethod("getName").invoke(product) as String
            val price = product.javaClass.getMethod("getPrice").invoke(product) as Int

            EventTrigger.fireProductViewWithDataSilently(this, category, productId, name, price)

            findViewById<TextView>(R.id.tvProductName).text = name
            findViewById<TextView>(R.id.tvCategory).text = category
        } catch (t: Throwable) {
            Log.w(TAG, "QA product view dispatch failed, fallback", t)
            EventTrigger.fireProductViewSilently(this)
        }
    }
}
