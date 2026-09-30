package com.aksworns22.setty.network

class ApiException(
    val code: String,
    override val message: String,
) : Exception(message)
