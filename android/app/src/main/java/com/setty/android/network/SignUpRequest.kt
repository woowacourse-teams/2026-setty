package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class SignUpRequest(
    val loginId: String,
    val password: String,
    val phoneNumber: String,
    val address: String,
    val dongCode: String,
)
