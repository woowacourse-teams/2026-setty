package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class DongListResponse(
    val items: List<DongResponse>,
)

@Serializable
data class DongResponse(
    val code: String,
    val name: String,
    val district: String,
)
