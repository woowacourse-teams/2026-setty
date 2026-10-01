package com.aksworns22.setty.data

import com.aksworns22.setty.model.Listing
import com.aksworns22.setty.model.ListingCategory
import com.aksworns22.setty.model.ListingDetail
import com.aksworns22.setty.model.SaleStatus
import com.aksworns22.setty.network.ListingDetailResponse
import com.aksworns22.setty.network.ListingSummaryResponse
import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.toApiException
import retrofit2.HttpException

class ListingRepository(
    private val settyNetworkApi: SettyNetworkApi,
) {
    suspend fun getListings(): List<Listing> {
        return settyNetworkApi.getListings().items.map { it.toListing() }
    }

    suspend fun getListingDetail(listingId: Long): ListingDetail {
        try {
            return settyNetworkApi.getListingDetail(listingId).toListingDetail()
        } catch (e: HttpException) {
            throw e.toApiException()
        }
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

internal fun ListingDetailResponse.toListingDetail() = ListingDetail(
    id = id,
    title = title,
    description = description,
    price = price,
    deliveryFee = deliveryFee,
    totalPrice = totalPrice,
    category = ListingCategory.fromOrNull(category),
    conditionGrade = conditionGrade,
    widthCm = dimensions.widthCm,
    depthCm = dimensions.depthCm,
    heightCm = dimensions.heightCm,
    // 알 수 없는 상태는 구매할 수 없도록 판매완료로 취급한다
    saleStatus = SaleStatus.fromOrNull(saleStatus) ?: SaleStatus.SOLD,
    imageUrls = images.sortedBy { it.displayOrder }.map { it.url },
)
