package com.aksworns22.setty.data

import com.aksworns22.setty.model.Listing
import com.aksworns22.setty.model.ListingCategory
import com.aksworns22.setty.network.ListingSummaryResponse
import com.aksworns22.setty.network.SettyNetworkApi

class ListingRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    suspend fun getListings(): List<Listing> {
        return settyNetworkApi.getListings().items.map { it.toListing() }
    }
}

private fun ListingSummaryResponse.toListing() = Listing(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    price = price,
    deliveryFee = deliveryFee,
    totalPrice = totalPrice,
    category = ListingCategory.fromOrNull(category),
    conditionGrade = conditionGrade,
    createdAt = createdAt,
)
