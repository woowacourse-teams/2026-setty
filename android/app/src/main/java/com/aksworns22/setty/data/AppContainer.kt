package com.aksworns22.setty.data

import android.content.Context
import com.aksworns22.setty.BuildConfig
import com.aksworns22.setty.network.AuthInterceptor
import com.aksworns22.setty.network.SettyNetworkApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AppContainer(
    private val context: Context
) {
    private val tokenLocalDataSource =
        TokenLocalDataSource(context.applicationContext)

    private val authInterceptor =
        AuthInterceptor(tokenLocalDataSource)
    private val okHttpClient: OkHttpClient = OkHttpClient
        .Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addNetworkInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
            addInterceptor(authInterceptor)
        }
        .build()
    private val retrofit: Retrofit =
        Retrofit.Builder().client(okHttpClient).baseUrl(BuildConfig.BASE_URL).addConverterFactory(
            Json { ignoreUnknownKeys = true }.asConverterFactory(
                "application/json".toMediaType()
            )
        ).build()
    private val settyNetworkApi = retrofit.create(SettyNetworkApi::class.java)

    val userRepository = UserRepository(
        settyNetworkApi = settyNetworkApi,
        tokenLocalDataSource = tokenLocalDataSource,
    )
}
