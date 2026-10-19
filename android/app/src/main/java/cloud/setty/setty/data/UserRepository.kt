package cloud.setty.setty.data

import cloud.setty.setty.model.Member
import cloud.setty.setty.network.AboutMeResponse
import cloud.setty.setty.network.DongUpdateRequest
import cloud.setty.setty.network.LoginRequest
import cloud.setty.setty.network.MemberUpdateRequest
import cloud.setty.setty.network.SettyNetworkApi
import cloud.setty.setty.network.SignUpRequest
import cloud.setty.setty.network.apiCall
import retrofit2.HttpException
import java.io.IOException

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
        dongCode: String,
    ) {
        apiCall { settyNetworkApi.signUp(SignUpRequest(loginId, password, phoneNumber, address, dongCode)) }
    }

    suspend fun getMe(): Member = apiCall { settyNetworkApi.aboutMe() }.toMember()

    suspend fun updateMe(phoneNumber: String, address: String): Member =
        apiCall { settyNetworkApi.updateMe(MemberUpdateRequest(phoneNumber, address)) }.toMember()

    suspend fun updateDong(dongCode: String): Member =
        apiCall { settyNetworkApi.updateDong(DongUpdateRequest(dongCode)) }.toMember()

    /** 서버 응답과 상관없이 저장한 토큰을 지운다. */
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

private fun AboutMeResponse.toMember() = Member(
    id = id.toLong(),
    loginId = loginId,
    phoneNumber = phoneNumber,
    address = address,
    dongCode = dongCode,
)
