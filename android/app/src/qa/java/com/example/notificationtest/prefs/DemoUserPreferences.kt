package com.example.notificationtest.prefs

import android.content.Context

/**
 * QA 빌드 전용 — 현재 페르소나 user_id를 SharedPreferences에 저장.
 * 기본값은 박준호 (user-142). DebugActivity의 사용자 셀렉터가 이 값을 변경한다.
 */
object DemoUserPreferences {

    private const val PREFS = "butterfly_qa"
    private const val KEY_USER_ID = "current_user_id"
    const val DEFAULT_USER_ID = "user-142"

    val knownUsers: List<String> = listOf(
        "user-73",       // 이지은
        "user-142",      // 박준호
        "user-200",      // 노이즈 1
        "user-201",      // 노이즈 2
        "user-202"       // 노이즈 3
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
