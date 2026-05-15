package com.example.notificationtest.butterfly

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.example.notificationtest.butterfly.device.DeviceManager
import com.example.notificationtest.butterfly.dto.ApiResponse
import com.example.notificationtest.butterfly.dto.DeviceRegisterRequest
import com.example.notificationtest.butterfly.dto.DeviceRegisterResponse
import com.example.notificationtest.butterfly.dto.EventCollectRequest
import com.example.notificationtest.butterfly.dto.EventCollectResponse
import com.example.notificationtest.butterfly.network.ApiClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object EventTrigger {

    private const val TAG = "EventTrigger"

    private const val DEMO_USER_ID = "user-142"

    /**
     * 5/22 시연용 — 메인 상품 정보 단일 출처.
     * fireProductView / fireProductViewSilently / firePurchase 가 모두 참조.
     * 시연 직전 변경 시 이 4 상수만 수정하면 됨.
     */
    private const val DEMO_PRODUCT_ID = "sku-9847"
    private const val DEMO_PRODUCT_NAME = "데일리 러닝화"
    private const val DEMO_PRODUCT_CATEGORY = "운동화"
    private const val DEMO_PRODUCT_PRICE = 89000

    /**
     * QA 빌드는 DemoUserPreferences 에서 동적으로, demo 빌드는 DEMO_USER_ID 상수.
     * BuildConfig.IS_QA_BUILD 분기는 reflection 없이 컴파일 시 결정.
     * main source 가 qa source 의 DemoUserPreferences 를 직접 import 할 수 없어 reflection.
     */
    @JvmStatic
    fun resolveUserId(context: Context): String {
        return if (com.example.notificationtest.BuildConfig.IS_QA_BUILD) {
            try {
                val clazz = Class.forName("com.example.notificationtest.prefs.DemoUserPreferences")
                val method = clazz.getMethod("currentUserId", Context::class.java)
                val instance = clazz.getField("INSTANCE").get(null)
                method.invoke(instance, context) as String
            } catch (t: Throwable) {
                Log.w(TAG, "DemoUserPreferences lookup failed, fallback to DEMO_USER_ID", t)
                DEMO_USER_ID
            }
        } else {
            DEMO_USER_ID
        }
    }

    @JvmStatic
    fun registerDevice(context: Context, fcmToken: String) {
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        val request = DeviceRegisterRequest(
            deviceId = deviceId,
            userId = resolveUserId(context),
            platform = "ANDROID",
            pushToken = fcmToken.ifBlank { "no_token" },
            appVersion = "1.0.0",
            osVersion = Build.VERSION.RELEASE ?: "unknown"
        )
        ApiClient.api.registerDevice(request)
            .enqueue(makeCallback<DeviceRegisterResponse>(context, "디바이스 등록 ($deviceId)"))
    }

    @JvmStatic
    fun registerDeviceSilently(context: Context, fcmToken: String) {
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        val request = DeviceRegisterRequest(
            deviceId = deviceId,
            userId = resolveUserId(context),
            platform = "ANDROID",
            pushToken = fcmToken.ifBlank { "no_token" },
            appVersion = "1.0.0",
            osVersion = Build.VERSION.RELEASE ?: "unknown"
        )
        ApiClient.api.registerDevice(request).enqueue(silentCallback("디바이스 등록"))
    }

    @JvmStatic
    fun fireAppOpen(context: Context) {
        sendEvent(context, "app_open", mapOf("appVersion" to "1.0.0"))
    }

    @JvmStatic
    fun fireProductView(context: Context) {
        sendEvent(context, "product_view", productViewPayload())
    }

    @JvmStatic
    fun fireProductViewSilently(context: Context) {
        sendEventSilently(context, "product_view", productViewPayload())
    }

    @JvmStatic
    fun firePurchase(context: Context) {
        val orderId = "O-${System.currentTimeMillis()}"
        sendEvent(context, "purchase", mapOf(
            "orderId" to orderId,
            "productId" to DEMO_PRODUCT_ID,
            "totalAmount" to DEMO_PRODUCT_PRICE
        ))
    }

    @JvmStatic
    fun fireAppOpenSilently(context: Context) {
        sendEventSilently(context, "app_open", mapOf("appVersion" to "1.0.0"))
    }

    @JvmStatic
    fun fireSearchSilently(context: Context, query: String, resultCount: Int) {
        sendEventSilently(context, "search", mapOf(
            "query" to query,
            "resultCount" to resultCount
        ))
    }

    @JvmStatic
    fun fireCategoryViewSilently(context: Context, category: String) {
        sendEventSilently(context, "category_view", mapOf("category" to category))
    }

    @JvmStatic
    fun fireProductClickSilently(
        context: Context, category: String, productId: String, price: Int
    ) {
        sendEventSilently(context, "product_click", mapOf(
            "category" to category,
            "productId" to productId,
            "price" to price
        ))
    }

    @JvmStatic
    fun fireProductViewWithDataSilently(
        context: Context, category: String, productId: String,
        productName: String, price: Int
    ) {
        sendEventSilently(context, "product_view", mapOf(
            "category" to category,
            "productId" to productId,
            "productName" to productName,
            "price" to price
        ))
    }

    @JvmStatic
    fun fireAddToCartSilently(
        context: Context, category: String, productId: String, quantity: Int, price: Int
    ) {
        sendEventSilently(context, "add_to_cart", mapOf(
            "category" to category,
            "productId" to productId,
            "quantity" to quantity,
            "price" to price
        ))
    }

    @JvmStatic
    fun fireCartViewSilently(context: Context, itemCount: Int, totalPrice: Int) {
        sendEventSilently(context, "cart_view", mapOf(
            "itemCount" to itemCount,
            "totalPrice" to totalPrice
        ))
    }

    @JvmStatic
    fun firePurchaseSilently(
        context: Context, orderId: String,
        items: List<Map<String, Any>>, totalAmount: Int
    ) {
        sendEventSilently(context, "purchase", mapOf(
            "orderId" to orderId,
            "items" to items,
            "totalAmount" to totalAmount
        ))
    }

    @JvmStatic
    fun fireWishlistAddSilently(context: Context, category: String, productId: String) {
        sendEventSilently(context, "wishlist_add", mapOf(
            "category" to category,
            "productId" to productId
        ))
    }

    @JvmStatic
    fun fireLoginSilently(context: Context, userId: String) {
        sendEventSilently(context, "login", mapOf("userId" to userId))
    }

    private fun productViewPayload(): Map<String, Any> = mapOf(
        "category" to DEMO_PRODUCT_CATEGORY,
        "productId" to DEMO_PRODUCT_ID,
        "productName" to DEMO_PRODUCT_NAME,
        "price" to DEMO_PRODUCT_PRICE
    )

    private fun sendEvent(context: Context, category: String, data: Map<String, Any>) {
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        val request = EventCollectRequest(
            deviceId = deviceId,
            userId = resolveUserId(context),
            eventCategory = category,
            occurredAt = nowIsoLocal(),
            eventData = data
        )
        ApiClient.api.collectEvent(request)
            .enqueue(makeCallback<EventCollectResponse>(context, category))
    }

    private fun sendEventSilently(context: Context, category: String, data: Map<String, Any>) {
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        val request = EventCollectRequest(
            deviceId = deviceId,
            userId = resolveUserId(context),
            eventCategory = category,
            occurredAt = nowIsoLocal(),
            eventData = data
        )
        ApiClient.api.collectEvent(request)
            .enqueue(silentCallback(category))
    }

    private fun nowIsoLocal(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("Asia/Seoul")
        return fmt.format(Date())
    }

    private fun <T> makeCallback(context: Context, label: String): Callback<ApiResponse<T>> =
        object : Callback<ApiResponse<T>> {
            override fun onResponse(
                call: Call<ApiResponse<T>>,
                response: Response<ApiResponse<T>>
            ) {
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Log.d(TAG, "[$label] OK ${body.data}")
                    Toast.makeText(context, "[$label] 성공", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "[$label] FAIL code=${response.code()} err=${body?.error}")
                    Toast.makeText(
                        context,
                        "[$label] 실패 ${response.code()} ${body?.error?.code ?: ""}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse<T>>, t: Throwable) {
                Log.e(TAG, "[$label] network error", t)
                Toast.makeText(context, "[$label] 네트워크 오류: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }

    private fun <T> silentCallback(label: String): Callback<ApiResponse<T>> =
        object : Callback<ApiResponse<T>> {
            override fun onResponse(
                call: Call<ApiResponse<T>>,
                response: Response<ApiResponse<T>>
            ) {
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Log.d(TAG, "[$label] OK ${body.data}")
                } else {
                    Log.w(TAG, "[$label] FAIL code=${response.code()} err=${body?.error}")
                }
            }

            override fun onFailure(call: Call<ApiResponse<T>>, t: Throwable) {
                Log.e(TAG, "[$label] network error", t)
            }
        }
}
