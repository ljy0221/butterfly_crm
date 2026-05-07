package com.example.notificationtest.butterfly.device

import android.content.Context
import java.util.UUID

object DeviceManager {

    private const val PREFS = "butterfly_device"
    private const val KEY_DEVICE_ID = "device_id"

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val newId = "dev_" + UUID.randomUUID().toString().replace("-", "").take(16)
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }
}
