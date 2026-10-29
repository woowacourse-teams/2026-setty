package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class KeywordRequest(
    val keyword: String,
)
