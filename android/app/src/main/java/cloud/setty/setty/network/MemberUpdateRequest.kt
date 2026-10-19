package cloud.setty.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class MemberUpdateRequest(
    val phoneNumber: String,
    val address: String,
)
