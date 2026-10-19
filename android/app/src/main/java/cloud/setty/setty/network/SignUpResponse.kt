package cloud.setty.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class SignUpResponse(
    val id: Long,
    val loginId: String,
    val role: String,
)
