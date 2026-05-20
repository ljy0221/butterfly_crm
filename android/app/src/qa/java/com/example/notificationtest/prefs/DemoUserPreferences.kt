package com.example.notificationtest.prefs

import android.content.Context

/**
 * QA 빌드 전용 — 현재 시연 사용자 external_id를 SharedPreferences에 저장.
 * DebugActivity의 사용자 셀렉터가 이 값을 변경한다.
 */
object DemoUserPreferences {

    private const val PREFS = "butterfly_qa"
    private const val KEY_USER_ID = "current_user_id"
    const val DEFAULT_USER_ID = "demo-user-1"

    val knownUsers: List<String> = listOf(
        "demo-user-1",
        "demo-user-2",
        "demo-user-3",
        "demo-user-4"
    )

    fun currentUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, DEFAULT_USER_ID) ?: DEFAULT_USER_ID
    }

    fun setCurrentUserId(context: Context, userId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, userId)
            .apply()
    }
}
