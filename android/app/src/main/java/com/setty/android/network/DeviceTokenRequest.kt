package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class DeviceTokenRequest(
    val token: String,
    val platform: String,
)
