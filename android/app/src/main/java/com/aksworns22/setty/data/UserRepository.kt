package com.aksworns22.setty.data

import com.aksworns22.setty.network.LoginRequest
import com.aksworns22.setty.network.SettyNetworkApi

class UserRepository(
    private val settyNetworkApi: SettyNetworkApi,
    private val tokenLocalDataSource: TokenLocalDataSource
) {
    suspend fun login(username: String, password: String) {
        val result = settyNetworkApi.login(LoginRequest(username, password))
        tokenLocalDataSource.writeCredentialToken(result.token)
    }

    suspend fun aboutMe() {
        settyNetworkApi.aboutMe()
    }
}
