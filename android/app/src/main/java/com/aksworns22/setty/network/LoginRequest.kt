package com.aksworns22.setty.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    @SerialName("loginId") val username: String,
    val password: String
)
