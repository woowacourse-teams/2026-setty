package com.setty.android.network

class ApiException(
    val code: String,
    override val message: String,
) : Exception(message)
