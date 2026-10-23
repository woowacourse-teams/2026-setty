package com.setty.android.devtools.scenario

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

internal class FailureScenarioInterceptor(
    private val isEnabled: suspend (FailureScenario) -> Boolean
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val scenario = FailureScenario.entries.firstOrNull { scenario ->
            runBlocking { isEnabled(scenario) } && scenario.matches(request.method, request.url.encodedPath)
        } ?: return chain.proceed(request)
        return scenario.toResponse(request)
    }
}

private fun FailureScenario.toResponse(request: Request): Response =
    Response.Builder()
        .request(request)
        .protocol(protocol = Protocol.HTTP_1_1)
        .code(statusCode)
        .message("Setty DevTool: $name")
        .build()
