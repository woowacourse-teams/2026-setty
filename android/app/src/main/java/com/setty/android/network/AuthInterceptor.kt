package com.setty.android.network

import com.setty.android.data.TokenLocalDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AuthInterceptor(
    private val tokenLocalDataSource: TokenLocalDataSource
): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            tokenLocalDataSource.getCredentialToken().first()
        }
        val request = chain.request().newBuilder().apply {
            if (token.isNotBlank()) {
                header("Authorization", "Bearer $token")
            }
        }.build()
        val response = chain.proceed(request)
        if (token.isNotBlank() && isInvalidTokenResponse(
                response.code,
                if (response.code == 401) response.peekBody(4096).string() else "",
            )) {
            runBlocking { tokenLocalDataSource.clearCredentialTokenIfMatches(token) }
        }
        return response
    }

}

internal fun isInvalidTokenResponse(status: Int, body: String): Boolean =
    status == 401 && runCatching {
        Json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.content == "INVALID_TOKEN"
    }.getOrDefault(false)
