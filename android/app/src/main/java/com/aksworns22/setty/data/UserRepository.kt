package com.aksworns22.setty.data

import com.aksworns22.setty.model.Member
import com.aksworns22.setty.network.AboutMeResponse
import com.aksworns22.setty.network.LoginRequest
import com.aksworns22.setty.network.MemberUpdateRequest
import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.SignUpRequest
import com.aksworns22.setty.network.apiCall

class UserRepository(
    private val settyNetworkApi: SettyNetworkApi,
    private val tokenLocalDataSource: TokenLocalDataSource
) {
    suspend fun login(username: String, password: String) {
        val result = apiCall { settyNetworkApi.login(LoginRequest(username, password)) }
        tokenLocalDataSource.writeCredentialToken(result.token)
    }

    suspend fun signUp(
        loginId: String,
        password: String,
        phoneNumber: String,
        address: String,
    ) {
        apiCall { settyNetworkApi.signUp(SignUpRequest(loginId, password, phoneNumber, address)) }
    }

    suspend fun getMe(): Member = apiCall { settyNetworkApi.aboutMe() }.toMember()

    suspend fun updateMe(phoneNumber: String, address: String): Member =
        apiCall { settyNetworkApi.updateMe(MemberUpdateRequest(phoneNumber, address)) }.toMember()

    /** 서버 응답과 상관없이 저장한 토큰을 지운다. */
    suspend fun logout() {
        runCatching { settyNetworkApi.logout() }
        tokenLocalDataSource.clearCredentialToken()
    }
}

private fun AboutMeResponse.toMember() = Member(
    id = id.toLong(),
    loginId = loginId,
    phoneNumber = phoneNumber,
    address = address,
)
