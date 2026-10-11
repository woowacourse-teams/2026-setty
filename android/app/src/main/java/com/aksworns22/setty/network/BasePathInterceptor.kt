package com.aksworns22.setty.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Retrofit은 `/`로 시작하는 경로에서 BASE_URL의 경로를 버리므로 여기서 다시 붙인다. */
class BasePathInterceptor(baseUrl: String) : Interceptor {

    private val basePath = baseUrl.toHttpUrl().encodedPath.trimEnd('/')

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (basePath.isEmpty() || request.url.encodedPath.startsWith("$basePath/")) {
            return chain.proceed(request)
        }
        val url = request.url.newBuilder()
            .encodedPath(basePath + request.url.encodedPath)
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}
