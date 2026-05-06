package com.example.notificationtest;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FCM_Main";
    private static final int NOTIFICATION_PERMISSION_CODE = 100;
    private static final String CHANNEL_ID = "fcm_default_channel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        requestNotificationPermission();
        createNotificationChannel();

        // 푸시 알림 클릭으로 앱이 열렸는지 확인
        handleNotificationClick(getIntent());

        // 버튼 1: FCM 토큰 확인
        Button btnGetToken = findViewById(R.id.btnGetToken);
        btnGetToken.setOnClickListener(v -> {
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            String token = task.getResult();
                            Log.d(TAG, "========== FCM 토큰 ==========");
                            Log.d(TAG, token);
                            Log.d(TAG, "===============================");
                            Toast.makeText(this, "토큰이 Logcat에 출력되었습니다", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "FCM 토큰 가져오기 실패", task.getException());
                            Toast.makeText(this, "토큰 가져오기 실패", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // 버튼 2: 테스트 푸시 알림 보내기 (로컬 알림 → 클릭 시 로그 출력)
        Button btnTestNotification = findViewById(R.id.btnTestNotification);
        btnTestNotification.setOnClickListener(v -> {
            sendTestNotification();
            Toast.makeText(this, "테스트 알림을 보냈습니다. 알림을 클릭해보세요!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotificationClick(intent);
    }

    private void handleNotificationClick(Intent intent) {
        if (intent != null && intent.hasExtra("notification_title")) {
            String title = intent.getStringExtra("notification_title");
            String body = intent.getStringExtra("notification_body");
            Log.d(TAG, "========== 푸시 알림 클릭됨 ==========");
            Log.d(TAG, "Title: " + title);
            Log.d(TAG, "Body: " + body);
            Log.d(TAG, "====================================");
            Toast.makeText(this, "푸시 알림 클릭 로그가 Logcat에 출력되었습니다", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendTestNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("notification_title", "테스트 알림");
        intent.putExtra("notification_body", "이것은 테스트 푸시 알림입니다!");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // 알림 삭제(스와이프) 시 호출될 PendingIntent
        Intent dismissIntent = new Intent(this, NotificationDismissReceiver.class);
        dismissIntent.putExtra("notification_title", "테스트 알림");
        dismissIntent.putExtra("notification_body", "이것은 테스트 푸시 알림입니다!");
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                this, 1, dismissIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("테스트 알림")
                .setContentText("이것은 테스트 푸시 알림입니다!")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(dismissPendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1, builder.build());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "알림 권한 허용됨");
            } else {
                Log.d(TAG, "알림 권한 거부됨");
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "FCM 알림", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("FCM 푸시 알림 채널");
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }
}
