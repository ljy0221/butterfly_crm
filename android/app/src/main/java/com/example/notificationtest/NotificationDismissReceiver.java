package com.example.notificationtest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NotificationDismissReceiver extends BroadcastReceiver {

    private static final String TAG = "FCM_Main";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("notification_title");
        String body = intent.getStringExtra("notification_body");
        Log.d(TAG, "========== 푸시 알림 삭제됨 (스와이프) ==========");
        Log.d(TAG, "Title: " + title);
        Log.d(TAG, "Body: " + body);
        Log.d(TAG, "===============================================");
    }
}
