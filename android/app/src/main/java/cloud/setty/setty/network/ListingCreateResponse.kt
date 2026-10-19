package cloud.setty.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class ListingCreateResponse(
    val listingId: Long,
    val createdAt: String,
)
