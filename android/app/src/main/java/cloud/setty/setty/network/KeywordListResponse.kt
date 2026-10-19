package cloud.setty.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class KeywordListResponse(
    val items: List<KeywordResponse>,
)

@Serializable
data class KeywordResponse(
    val id: Long,
    val keyword: String,
    val createdAt: String,
)
