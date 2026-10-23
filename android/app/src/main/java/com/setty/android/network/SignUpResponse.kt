package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class SignUpResponse(
    val id: Long,
    val loginId: String,
    val role: String,
)
