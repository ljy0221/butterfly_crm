package com.example.notificationtest.butterfly.dto

data class ActionIngestRequest(
    val logId: Long? = null,
    val messageKey: String? = null,
    val userId: Long? = null,
    val deviceId: Long? = null,
    val actionType: String,
    val actionTarget: String? = null,
    val userAgent: String? = null,
    val actionAt: String
)

data class ActionIngestResponse(
    val actionId: Long,
    val acceptedAt: String
)
