package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class MemberUpdateRequest(
    val phoneNumber: String,
    val address: String,
)
