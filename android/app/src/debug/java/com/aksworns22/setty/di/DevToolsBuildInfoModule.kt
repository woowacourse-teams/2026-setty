package com.aksworns22.setty.di

import com.aksworns22.setty.BuildConfig
import com.aksworns22.setty.devtools.model.AppBuildInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DevToolsBuildInfoModule {
    @Provides
    fun providesAppBuildInfo(): AppBuildInfo = AppBuildInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toString(),
        buildType = BuildConfig.BUILD_TYPE,
        baseUrl = BuildConfig.BASE_URL,
    )
}
