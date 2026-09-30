package com.aksworns22.setty.data

import com.aksworns22.setty.network.LoginRequest
import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.SignUpRequest
import com.aksworns22.setty.network.toApiException
import retrofit2.HttpException
import java.io.IOException

class UserRepository(
    private val settyNetworkApi: SettyNetworkApi,
    private val tokenLocalDataSource: TokenLocalDataSource
) {
    suspend fun login(username: String, password: String) {
        val result = settyNetworkApi.login(LoginRequest(username, password))
        tokenLocalDataSource.writeCredentialToken(result.token)
    }

    suspend fun signUp(
        loginId: String,
        password: String,
        phoneNumber: String,
        address: String,
    ) {
        try {
            settyNetworkApi.signUp(SignUpRequest(loginId, password, phoneNumber, address))
        } catch (e: HttpException) {
            throw e.toApiException()
        }
    }

    suspend fun aboutMe() {
        settyNetworkApi.aboutMe()
    }

    suspend fun logout() {
        try {
            settyNetworkApi.logout()
        } catch (_: IOException) {
            // 서버에 알리지 못해도 기기에서는 로그아웃한다
        } catch (_: HttpException) {
            // 이미 만료된 토큰이어도 기기에서는 로그아웃한다
        } finally {
            tokenLocalDataSource.clearCredentialToken()
        }
    }
}
