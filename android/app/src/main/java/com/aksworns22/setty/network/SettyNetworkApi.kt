package com.aksworns22.setty.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path


interface SettyNetworkApi {
    @POST("/api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @POST("/api/auth/signup")
    suspend fun signUp(
        @Body request: SignUpRequest
    ): SignUpResponse

    @GET("/api/auth/me")
    suspend fun aboutMe(): AboutMeResponse

    @PUT("/api/auth/me")
    suspend fun updateMe(
        @Body request: MemberUpdateRequest
    ): AboutMeResponse

    @POST("/api/auth/logout")
    suspend fun logout()

    @GET("/api/listings")
    suspend fun getListings(): ListingListResponse

    @GET("/api/listings/{listingId}")
    suspend fun getFurnitureDetail(
        @Path("listingId") listingId: Long
    ): FurnitureDetailResponse

    @Multipart
    @POST("/api/listings")
    suspend fun createListing(
        @Part("request") request: RequestBody,
        @Part images: List<MultipartBody.Part>,
    ): ListingCreateResponse

    @Multipart
    @PUT("/api/listings/{listingId}")
    suspend fun updateListing(
        @Path("listingId") listingId: Long,
        @Part("request") request: RequestBody,
        @Part images: List<MultipartBody.Part>,
    )

    @DELETE("/api/listings/{listingId}")
    suspend fun deleteListing(
        @Path("listingId") listingId: Long
    )

    @GET("/api/me/listings")
    suspend fun getMyListings(): MyListingListResponse

    @GET("/api/me/favorites")
    suspend fun getFavorites(): ListingListResponse

    @GET("/api/me/favorites/{listingId}")
    suspend fun getFavoriteStatus(
        @Path("listingId") listingId: Long
    ): FavoriteStatusResponse

    @PUT("/api/me/favorites/{listingId}")
    suspend fun addFavorite(
        @Path("listingId") listingId: Long
    )

    @DELETE("/api/me/favorites/{listingId}")
    suspend fun removeFavorite(
        @Path("listingId") listingId: Long
    )

    @POST("/api/orders")
    suspend fun createOrder(
        @Body request: OrderCreateRequest
    ): OrderCreateResponse

    @GET("/api/me/orders")
    suspend fun getMyOrders(): List<OrderResponse>

    @GET("/api/orders/{id}")
    suspend fun getOrder(
        @Path("id") orderId: Long
    ): OrderResponse

    @POST("/api/orders/{id}/cancellations")
    suspend fun cancelOrder(
        @Path("id") orderId: Long
    ): OrderCancellationResponse

    @POST("/api/payments/confirm")
    suspend fun confirmPayment(
        @Body request: PaymentConfirmRequest
    ): PaymentConfirmResponse

    @POST("/api/payments/fail")
    suspend fun failPayment(
        @Body request: PaymentFailRequest
    )

    @PUT("/api/me/device-token")
    suspend fun registerDeviceToken(
        @Body request: DeviceTokenRequest
    )

    @HTTP(method = "DELETE", path = "/api/me/device-token", hasBody = true)
    suspend fun unregisterDeviceToken(
        @Body request: DeviceTokenDeleteRequest
    )

    @POST("/api/me/notifications/{notificationId}/ack")
    suspend fun ackNotification(
        @Path("notificationId") notificationId: Long
    )

    @GET("/api/me/keywords")
    suspend fun getKeywords(): KeywordListResponse

    @PUT("/api/me/keywords")
    suspend fun addKeyword(
        @Body request: KeywordRequest
    )

    @DELETE("/api/me/keywords/{subscriptionId}")
    suspend fun removeKeyword(
        @Path("subscriptionId") subscriptionId: Long
    )
}
