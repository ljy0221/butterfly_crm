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

        // savedInstanceState == null 일 때만 — rotation/recreate 시 중복 발사 방지
        if (savedInstanceState == null) {
            EventTrigger.fireProductViewSilently(this)
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
}
