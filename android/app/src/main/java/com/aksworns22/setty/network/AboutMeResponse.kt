package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class AboutMeResponse(
    val address: String,
    val id: Int,
    val loginId: String,
    val phoneNumber: String,
    val role: String
)
