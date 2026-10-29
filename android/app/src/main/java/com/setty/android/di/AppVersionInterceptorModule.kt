package com.setty.android.di

import com.setty.android.network.AppVersionInterceptor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor

// AppContainer가 Hilt의 Set<Interceptor>를 받아 붙이므로 집합에 넣기만 하면 된다
@Module
@InstallIn(SingletonComponent::class)
abstract class AppVersionInterceptorModule {
    @Binds
    @IntoSet
    abstract fun bindsAppVersionInterceptor(interceptor: AppVersionInterceptor): Interceptor
}
