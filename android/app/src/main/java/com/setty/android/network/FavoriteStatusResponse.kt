package com.setty.android.network

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteStatusResponse(
    val favorited: Boolean,
)
