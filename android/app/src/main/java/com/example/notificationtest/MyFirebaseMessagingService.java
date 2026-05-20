package com.example.notificationtest;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_Service";
    private static final String CHANNEL_ID = "fcm_default_channel";
    private static final String DATA_MESSAGE_KEY = "butterfly_message_key";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "새로운 FCM 토큰 발급: " + token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "푸시 메시지 수신!");
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Data: " + remoteMessage.getData());
        }

        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            Log.d(TAG, "Title: " + title);
            Log.d(TAG, "Body: " + body);
            showNotification(title, body, remoteMessage.getData());
        }
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra("notification_title", title);
        intent.putExtra("notification_body", body);
        for (Map.Entry<String, String> entry : data.entrySet()) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = data.containsKey(DATA_MESSAGE_KEY)
                ? data.get(DATA_MESSAGE_KEY).hashCode()
                : 0;
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 알림 삭제(스와이프) 시 호출될 PendingIntent
        Intent dismissIntent = new Intent(this, NotificationDismissReceiver.class);
        dismissIntent.putExtra("notification_title", title);
        dismissIntent.putExtra("notification_body", body);
        for (Map.Entry<String, String> entry : data.entrySet()) {
            dismissIntent.putExtra(entry.getKey(), entry.getValue());
        }
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                this, requestCode + 1, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(dismissPendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(requestCode, builder.build());
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
