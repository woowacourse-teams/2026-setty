package cloud.setty.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class AboutMeResponse(
    val address: String,
    val id: Int,
    val loginId: String,
    val phoneNumber: String,
    val role: String,
    // 행정동을 고르기 전에 가입한 회원은 비어 있다
    val dongCode: String? = null,
)
