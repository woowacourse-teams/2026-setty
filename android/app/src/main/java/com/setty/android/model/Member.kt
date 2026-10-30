package com.setty.android.model

data class Member(
    val id: Long,
    val loginId: String,
    val phoneNumber: String,
    val address: String,
    val dongCode: String?,
)
