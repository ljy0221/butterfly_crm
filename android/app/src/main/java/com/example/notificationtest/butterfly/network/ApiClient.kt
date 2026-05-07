package com.example.notificationtest.butterfly.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // 에뮬레이터: 10.0.2.2 = 호스트 PC. 실기기는 PC IP로 변경.
    private const val BASE_URL = "http://10.0.2.2:8080/"
    private const val PROJECT_ID = "1"

    val api: ButterflyApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val projectIdInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-Project-Id", PROJECT_ID)
                .build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(projectIdInterceptor)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ButterflyApi::class.java)
    }
}
