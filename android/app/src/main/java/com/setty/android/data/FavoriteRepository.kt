package com.setty.android.data

import com.setty.android.core.model.FurnitureSummary
import com.setty.android.network.SettyNetworkApi
import com.setty.android.network.apiCall

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
