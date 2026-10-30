package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class DeviceTokenDeleteRequest(
    val token: String,
)
