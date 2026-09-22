package com.aksworns22.setty.data

import com.aksworns22.setty.network.LoginRequest
import com.aksworns22.setty.network.SettyNetworkApi

class UserRepository(
    private val settyNetworkApi: SettyNetworkApi
) {
    suspend fun login(username: String, password: String) {
        settyNetworkApi.login(LoginRequest(username, password))
    }
}
