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

    /**
     * 5/22 시연용 — 박준호 페르소나 외부 ID.
     * 백엔드 시드 데이터 (users.external_id) 와 매칭된다.
     * 운영 빌드에서는 SDK 가 사용자별로 외부 ID 를 주입하는 경로로 교체 필요.
     */
    private const val DEMO_USER_ID = "user-142"

    @JvmStatic
    fun registerDevice(context: Context, fcmToken: String) {
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        val request = DeviceRegisterRequest(
            deviceId = deviceId,
            userId = DEMO_USER_ID,
            platform = "ANDROID",
            pushToken = fcmToken.ifBlank { "no_token" },
            appVersion = "1.0.0",
            osVersion = Build.VERSION.RELEASE ?: "unknown"
        )
        ApiClient.api.registerDevice(request)
            .enqueue(makeCallback<DeviceRegisterResponse>(context, "디바이스 등록 ($deviceId)"))
    }

    @JvmStatic
    fun fireAppOpen(context: Context) {
        sendEvent(context, "app_open", mapOf("appVersion" to "1.0.0"))
    }

    @JvmStatic
    fun fireProductView(context: Context) {
        sendEvent(context, "product_view", mapOf(
            "category" to "운동화",
            "productId" to "sku-9847",
            "productName" to "데일리 러닝화",
            "price" to 89000
        ))
    }

    @JvmStatic
    fun firePurchase(context: Context) {
        val orderId = "O-${System.currentTimeMillis()}"
        sendEvent(context, "purchase", mapOf(
            "orderId" to orderId,
            "productId" to "sku-9847",
            "totalAmount" to 89000
        ))
    }

    private fun sendEvent(context: Context, category: String, data: Map<String, Any>) {
        val deviceId = DeviceManager.getOrCreateDeviceId(context)
        val request = EventCollectRequest(
            deviceId = deviceId,
            userId = DEMO_USER_ID,
            eventCategory = category,
            occurredAt = nowIsoLocal(),
            eventData = data
        )
        ApiClient.api.collectEvent(request)
            .enqueue(makeCallback<EventCollectResponse>(context, category))
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
}
