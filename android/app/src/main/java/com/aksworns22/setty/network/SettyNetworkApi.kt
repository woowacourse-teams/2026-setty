package com.aksworns22.setty.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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

    @POST("/api/auth/logout")
    suspend fun logout()

    @GET("/api/listings")
    suspend fun getListings(): ListingListResponse

    @GET("/api/listings/{listingId}")
    suspend fun getListingDetail(
        @Path("listingId") listingId: Long
    ): ListingDetailResponse

    @GET("/api/me/listings")
    suspend fun getMyListings(): MyListingListResponse
}
