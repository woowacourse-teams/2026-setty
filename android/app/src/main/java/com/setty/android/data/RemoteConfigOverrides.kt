package com.setty.android.data

fun interface RemoteConfigOverrides {
    fun get(key: String): String?
}

