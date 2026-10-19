package cloud.setty.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class KeywordRequest(
    val keyword: String,
)
