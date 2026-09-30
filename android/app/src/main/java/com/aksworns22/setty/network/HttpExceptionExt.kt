package com.aksworns22.setty.network

import kotlinx.serialization.json.Json
import retrofit2.HttpException

private val json = Json { ignoreUnknownKeys = true }

fun HttpException.toApiException(): Exception {
    val errorBody = response()?.errorBody()?.string() ?: return this
    return runCatching {
        val errorResponse = json.decodeFromString<ErrorResponse>(errorBody)
        ApiException(errorResponse.code, errorResponse.message)
    }.getOrDefault(this)
}
