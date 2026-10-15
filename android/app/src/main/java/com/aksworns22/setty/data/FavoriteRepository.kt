package com.aksworns22.setty.data

import com.aksworns22.setty.core.model.FurnitureSummary
import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.apiCall

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
