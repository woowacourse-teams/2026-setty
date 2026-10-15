package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class DeviceTokenDeleteRequest(
    val token: String,
)
