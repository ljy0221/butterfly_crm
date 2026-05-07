package com.example.notificationtest.butterfly.dto

data class EventCollectRequest(
    val deviceId: String,
    val userId: String? = null,
    val eventCategory: String,
    val occurredAt: String,
    val eventData: Map<String, Any>? = null
)

data class EventCollectResponse(
    val eventId: String,
    val acceptedAt: String,
    val schemaVersion: Int
)
