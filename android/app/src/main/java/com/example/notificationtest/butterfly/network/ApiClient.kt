package com.example.notificationtest.butterfly.network

import com.example.notificationtest.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // 5/22 시연용 — BASE_URL은 BuildConfig 로 외부화 (build.gradle 의 buildConfigField).
    // 에뮬레이터: 10.0.2.2 = 호스트 PC. 실기기 D-3 리허설에서 LAN IP 로 교체.
    private val BASE_URL: String = BuildConfig.BUTTERFLY_API_BASE_URL
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
