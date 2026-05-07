package com.example.notificationtest.butterfly.dto

data class DeviceRegisterRequest(
    val deviceId: String,
    val userId: String? = null,
    val platform: String,
    val pushToken: String,
    val appVersion: String,
    val osVersion: String
)

data class DeviceRegisterResponse(
    val tokenValid: Boolean
)
