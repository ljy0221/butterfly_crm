package com.example.notificationtest

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.notificationtest.butterfly.EventTrigger
import com.example.notificationtest.butterfly.device.DeviceManager
import com.google.firebase.messaging.FirebaseMessaging

/**
 * 시연자 비상 진입용 — 청중에게 노출되지 않는다.
 * 기존 MainActivity 의 기능을 그대로 이전.
 */
class DebugActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FCM_Debug"
        private const val NOTIFICATION_PERMISSION_CODE = 100
        private const val CHANNEL_ID = "fcm_default_channel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        requestNotificationPermission()
        createNotificationChannel()
        handleNotificationClick(intent)

        findViewById<Button>(R.id.btnGetToken).setOnClickListener {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "========== FCM 토큰 ==========")
                    Log.d(TAG, task.result.orEmpty())
                    Log.d(TAG, "===============================")
                    Toast.makeText(this, "토큰이 Logcat에 출력되었습니다", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "FCM 토큰 가져오기 실패", task.exception)
                    Toast.makeText(this, "토큰 가져오기 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnTestNotification).setOnClickListener {
            sendTestNotification()
            Toast.makeText(this, "테스트 알림을 보냈습니다. 알림을 클릭해보세요!", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.tvDeviceId).text =
            "deviceId: " + DeviceManager.getOrCreateDeviceId(this)

        findViewById<Button>(R.id.btnRegisterDevice).setOnClickListener {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = if (task.isSuccessful && task.result != null) task.result else "no_token"
                EventTrigger.registerDevice(this, token!!)
            }
        }

        findViewById<Button>(R.id.btnEventAppOpen).setOnClickListener {
            EventTrigger.fireAppOpen(this)
        }
        findViewById<Button>(R.id.btnEventProductView).setOnClickListener {
            EventTrigger.fireProductView(this)
        }
        findViewById<Button>(R.id.btnEventPurchase).setOnClickListener {
            EventTrigger.firePurchase(this)
        }

        // QA 빌드 전용 패널
        if (BuildConfig.IS_QA_BUILD) {
            setupQaPanel()
        }
    }

    /**
     * QA 빌드 전용 — 사용자 셀렉터 + 5가지 이벤트 발사 버튼 활성화.
     * DemoUserPreferences 는 qa source set 에만 있으므로 reflection.
     */
    private fun setupQaPanel() {
        listOf(
            R.id.qaDivider, R.id.qaSection, R.id.spUser,
            R.id.btnFireSearch, R.id.btnFireCategoryView,
            R.id.btnFireAddToCart, R.id.btnFireWishlistAdd, R.id.btnFireLogin
        ).forEach { findViewById<android.view.View>(it).visibility = android.view.View.VISIBLE }

        try {
            val prefsClass = Class.forName("com.example.notificationtest.prefs.DemoUserPreferences")
            val instance = prefsClass.getField("INSTANCE").get(null)
            @Suppress("UNCHECKED_CAST")
            val knownUsers = prefsClass.getMethod("getKnownUsers").invoke(instance) as List<String>
            val currentUserId = prefsClass.getMethod("currentUserId", android.content.Context::class.java)
                .invoke(instance, this) as String

            val sp = findViewById<android.widget.Spinner>(R.id.spUser)
            val adapter = android.widget.ArrayAdapter(
                this, android.R.layout.simple_spinner_item, knownUsers
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            sp.adapter = adapter
            sp.setSelection(knownUsers.indexOf(currentUserId).coerceAtLeast(0))

            sp.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?,
                    view: android.view.View?, position: Int, id: Long) {
                    val newUser = knownUsers[position]
                    prefsClass.getMethod("setCurrentUserId",
                        android.content.Context::class.java, String::class.java)
                        .invoke(instance, this@DebugActivity, newUser)
                    EventTrigger.fireLoginSilently(this@DebugActivity, newUser)
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        } catch (t: Throwable) {
            Log.w(TAG, "QA user selector setup failed", t)
        }

        findViewById<Button>(R.id.btnFireSearch).setOnClickListener {
            EventTrigger.fireSearchSilently(this, "운동화", 8)
            Toast.makeText(this, "search 이벤트 발사", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnFireCategoryView).setOnClickListener {
            EventTrigger.fireCategoryViewSilently(this, "시계")
            Toast.makeText(this, "category_view 이벤트 발사", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnFireAddToCart).setOnClickListener {
            EventTrigger.fireAddToCartSilently(this, "운동화", "sku-shoe-1", 1, 57850)
            Toast.makeText(this, "add_to_cart 이벤트 발사", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnFireWishlistAdd).setOnClickListener {
            EventTrigger.fireWishlistAddSilently(this, "운동화", "sku-shoe-1")
            Toast.makeText(this, "wishlist_add 이벤트 발사", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnFireLogin).setOnClickListener {
            EventTrigger.fireLoginSilently(this, "user-test")
            Toast.makeText(this, "login 이벤트 발사", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationClick(intent)
    }

    private fun handleNotificationClick(intent: Intent?) {
        if (intent != null && intent.hasExtra("notification_title")) {
            val title = intent.getStringExtra("notification_title")
            val body = intent.getStringExtra("notification_body")
            Log.d(TAG, "========== 푸시 알림 클릭됨 ==========")
            Log.d(TAG, "Title: $title")
            Log.d(TAG, "Body: $body")
            Log.d(TAG, "====================================")
            Toast.makeText(this, "푸시 알림 클릭 로그가 Logcat에 출력되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendTestNotification() {
        val intent = Intent(this, DebugActivity::class.java).apply {
            putExtra("notification_title", "테스트 알림")
            putExtra("notification_body", "이것은 테스트 푸시 알림입니다!")
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = Intent(this, NotificationDismissReceiver::class.java).apply {
            putExtra("notification_title", "테스트 알림")
            putExtra("notification_body", "이것은 테스트 푸시 알림입니다!")
        }
        val dismissPending = PendingIntent.getBroadcast(
            this, 1, dismissIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("테스트 알림")
            .setContentText("이것은 테스트 푸시 알림입니다!")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setDeleteIntent(dismissPending)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, builder.build())
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
