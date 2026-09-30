package com.aksworns22.setty.data

import com.aksworns22.setty.network.LoginRequest
import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.SignUpRequest
import com.aksworns22.setty.network.toApiException
import retrofit2.HttpException

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
}
