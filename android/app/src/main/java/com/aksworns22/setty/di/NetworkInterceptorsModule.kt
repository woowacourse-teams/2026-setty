package com.aksworns22.setty.di

import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import okhttp3.Interceptor

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkInterceptorsModule {
    @Multibinds
    abstract fun interceptors(): Set<Interceptor>
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NetworkInterceptorsEntryPoint {
    fun interceptors(): Set<Interceptor>
}
