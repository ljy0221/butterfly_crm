package com.example.notificationtest.butterfly.network

import com.example.notificationtest.butterfly.dto.ApiResponse
import com.example.notificationtest.butterfly.dto.ActionIngestRequest
import com.example.notificationtest.butterfly.dto.ActionIngestResponse
import com.example.notificationtest.butterfly.dto.DeviceRegisterRequest
import com.example.notificationtest.butterfly.dto.DeviceRegisterResponse
import com.example.notificationtest.butterfly.dto.EventCollectRequest
import com.example.notificationtest.butterfly.dto.EventCollectResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ButterflyApi {

    @POST("api/v1/devices")
    fun registerDevice(
        @Body body: DeviceRegisterRequest
    ): Call<ApiResponse<DeviceRegisterResponse>>

    @POST("api/v1/events")
    fun collectEvent(
        @Body body: EventCollectRequest
    ): Call<ApiResponse<EventCollectResponse>>

    @POST("api/v1/events/actions")
    fun recordAction(
        @Body body: ActionIngestRequest
    ): Call<ApiResponse<ActionIngestResponse>>
}
