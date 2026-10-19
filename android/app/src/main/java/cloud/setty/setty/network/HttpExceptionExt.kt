package cloud.setty.setty.network

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

/** API를 호출하고 서버 에러 응답을 [ApiException]으로 바꿔 던진다. */
suspend fun <T> apiCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        throw e.toApiException()
    }
}
