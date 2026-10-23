package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class DongUpdateRequest(
    val dongCode: String,
)
