package com.aksworns22.setty.data

import android.net.Uri
import com.aksworns22.setty.core.model.FurnitureDetail
import com.aksworns22.setty.core.model.FurnitureSummary
import com.aksworns22.setty.core.model.SaleStatus
import com.aksworns22.setty.model.EditableListing
import com.aksworns22.setty.model.ListingDraft
import com.aksworns22.setty.model.ListingImage
import com.aksworns22.setty.model.MyFurniture
import com.aksworns22.setty.model.furnitureCategoryOf
import com.aksworns22.setty.model.saleStatusOrNull
import com.aksworns22.setty.network.DimensionsRequest
import com.aksworns22.setty.network.FurnitureDetailResponse
import com.aksworns22.setty.network.FurnitureSummaryResponse
import com.aksworns22.setty.network.ListingCreateRequest
import com.aksworns22.setty.network.ListingUpdateRequest
import com.aksworns22.setty.network.MyFurnitureResponse
import com.aksworns22.setty.network.SettyNetworkApi
import com.aksworns22.setty.network.apiCall
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class FurnitureRepository(
    private val settyNetworkApi: SettyNetworkApi,
    private val listingImageReader: ListingImageReader,
) {
    suspend fun getFurniture(): List<FurnitureSummary> {
        return settyNetworkApi.getListings().items.map { it.toFurniture() }
    }

    suspend fun getFurnitureDetail(furnitureId: Long): FurnitureDetail =
        apiCall { settyNetworkApi.getFurnitureDetail(furnitureId) }.toFurnitureDetail()

    suspend fun getMyFurniture(): List<MyFurniture> =
        apiCall { settyNetworkApi.getMyListings() }.items.map { it.toMyFurniture() }

    suspend fun getEditableListing(listingId: Long): EditableListing =
        apiCall { settyNetworkApi.getFurnitureDetail(listingId) }.toEditableListing()

    /** 매물을 등록하고 등록된 매물 id를 돌려준다. */
    suspend fun createListing(draft: ListingDraft, images: List<Uri>): Long {
        val imageParts = listingImageReader.read(images)
        val request = ListingCreateRequest(
            title = draft.title,
            description = draft.description,
            price = draft.price,
            category = draft.category.name,
            conditionGrade = draft.conditionGrade,
            dimensions = draft.toDimensionsRequest(),
        )
        return apiCall {
            settyNetworkApi.createListing(json.encodeToString(request).toJsonPart(), imageParts)
        }.listingId
    }

    /** 기존 이미지는 [retainedImageIds] 순서로 유지하고 [newImages]를 뒤에 추가한다. */
    suspend fun updateListing(
        listingId: Long,
        draft: ListingDraft,
        retainedImageIds: List<Long>,
        newImages: List<Uri>,
    ) {
        val imageParts = listingImageReader.read(newImages)
        val request = ListingUpdateRequest(
            title = draft.title,
            description = draft.description,
            price = draft.price,
            category = draft.category.name,
            conditionGrade = draft.conditionGrade,
            dimensions = draft.toDimensionsRequest(),
            retainedImageIds = retainedImageIds,
        )
        apiCall {
            settyNetworkApi.updateListing(listingId, json.encodeToString(request).toJsonPart(), imageParts)
        }
    }

    suspend fun deleteListing(listingId: Long) {
        apiCall { settyNetworkApi.deleteListing(listingId) }
    }

    companion object {
        private val json = Json { encodeDefaults = true }
    }
}

private fun String.toJsonPart(): RequestBody = toRequestBody("application/json".toMediaType())

private fun ListingDraft.toDimensionsRequest() = DimensionsRequest(
    widthCm = widthCm,
    depthCm = depthCm,
    heightCm = heightCm,
)

internal fun FurnitureSummaryResponse.toFurniture() = FurnitureSummary(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    price = price,
    deliveryFee = deliveryFee,
    totalPrice = totalPrice,
    category = furnitureCategoryOf(category),
    conditionGrade = conditionGrade,
    createdAt = createdAt,
)

internal fun FurnitureDetailResponse.toFurnitureDetail() = FurnitureDetail(
    id = id,
    title = title,
    description = description,
    price = price,
    deliveryFee = deliveryFee,
    totalPrice = totalPrice,
    category = furnitureCategoryOf(category),
    conditionGrade = conditionGrade,
    widthCm = dimensions.widthCm,
    depthCm = dimensions.depthCm,
    heightCm = dimensions.heightCm,
    saleStatus = saleStatusOrNull(saleStatus) ?: SaleStatus.SOLD,
    imageUrls = images.sortedBy { it.displayOrder }.map { it.url },
)

internal fun FurnitureDetailResponse.toEditableListing() = EditableListing(
    id = id,
    draft = ListingDraft(
        title = title,
        description = description,
        price = price,
        category = furnitureCategoryOf(category),
        conditionGrade = conditionGrade,
        widthCm = dimensions.widthCm,
        depthCm = dimensions.depthCm,
        heightCm = dimensions.heightCm,
    ),
    images = images.sortedBy { it.displayOrder }.map { ListingImage(it.id, it.url) },
)

internal fun MyFurnitureResponse.toMyFurniture() = MyFurniture(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    price = price,
    deliveryFee = deliveryFee,
    totalPrice = totalPrice,
    category = furnitureCategoryOf(category),
    conditionGrade = conditionGrade,
    saleStatus = saleStatusOrNull(saleStatus) ?: SaleStatus.SOLD,
    hasPurchaseRequest = hasPurchaseRequest,
    canUpdate = canUpdate,
    canDelete = canDelete,
    createdAt = createdAt,
)
