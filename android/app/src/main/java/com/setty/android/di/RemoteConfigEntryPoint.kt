package com.setty.android.di

import com.setty.android.data.RemoteConfigRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RemoteConfigEntryPoint {
    fun remoteConfigRepository(): RemoteConfigRepository
}
