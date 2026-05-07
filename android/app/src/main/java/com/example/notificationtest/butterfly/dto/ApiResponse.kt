package com.example.notificationtest.butterfly.dto

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ApiError?
)

data class ApiError(
    val code: String?,
    val message: String?
)
