package com.aksworns22.setty.model

/** 서울 행정동. [district]는 동이 속한 자치구 이름이다. */
data class Dong(
    val code: String,
    val name: String,
    val district: String,
) {
    val displayName: String
        get() = "$district $name"
}
