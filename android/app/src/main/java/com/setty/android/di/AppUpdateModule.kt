package com.setty.android.di

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {
    // release에서는 개발자 도구가 없어 비어 있으므로 실제 Play 업데이트를 쓴다
    @Provides
    @Singleton
    fun providesAppUpdateManager(
        @ApplicationContext context: Context,
        @DebugAppUpdateManager debugAppUpdateManager: Optional<AppUpdateManager>,
    ): AppUpdateManager = debugAppUpdateManager.orElseGet { AppUpdateManagerFactory.create(context) }
}
