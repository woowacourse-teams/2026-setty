package cloud.setty.setty.network

import cloud.setty.setty.data.TokenLocalDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

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
        return chain.proceed(request)
    }

}
