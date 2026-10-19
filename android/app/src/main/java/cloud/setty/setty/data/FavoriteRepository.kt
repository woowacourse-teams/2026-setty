package cloud.setty.setty.data

import cloud.setty.setty.core.model.FurnitureSummary
import cloud.setty.setty.network.SettyNetworkApi
import cloud.setty.setty.network.apiCall

class FavoriteRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    suspend fun getFavorites(): List<FurnitureSummary> =
        apiCall { settyNetworkApi.getFavorites() }.items.map { it.toFurniture() }

    suspend fun isFavorited(listingId: Long): Boolean =
        apiCall { settyNetworkApi.getFavoriteStatus(listingId) }.favorited

    suspend fun add(listingId: Long) {
        apiCall { settyNetworkApi.addFavorite(listingId) }
    }

    suspend fun remove(listingId: Long) {
        apiCall { settyNetworkApi.removeFavorite(listingId) }
    }
}
