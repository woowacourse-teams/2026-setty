package com.setty.android.di

import com.setty.android.BuildConfig
import com.setty.android.devtools.model.AppBuildInfo
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
