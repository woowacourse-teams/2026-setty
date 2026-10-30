package com.setty.android.di

import com.google.android.play.core.appupdate.AppUpdateManager
import com.setty.android.data.RemoteConfigOverrides
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// debug 빌드에서만 개발자 도구가 채운다. release에서는 Optional.empty()가 주입된다
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugHooksModule {
    @BindsOptionalOf
    abstract fun bindRemoteConfigOverrides(): RemoteConfigOverrides

    @BindsOptionalOf
    @DebugAppUpdateManager
    abstract fun bindDebugAppUpdateManager(): AppUpdateManager
}
