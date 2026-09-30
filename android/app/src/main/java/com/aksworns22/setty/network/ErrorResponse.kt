package com.aksworns22.setty.network

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)
