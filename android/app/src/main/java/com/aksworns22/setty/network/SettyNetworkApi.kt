package com.aksworns22.setty.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST


interface SettyNetworkApi {
    @POST("/api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @GET("/api/auth/me")
    suspend fun aboutMe(): AboutMeResponse
}
