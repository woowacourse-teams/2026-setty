package cloud.setty.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class DongUpdateRequest(
    val dongCode: String,
)
