package com.setty.android.di

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.setty.android.data.RemoteConfigOverrides
import com.setty.android.devtools.remoteconfig.RemoteConfigOverrideStore
import com.setty.android.devtools.update.DevToolsAppUpdate
import com.setty.android.devtools.update.UpdateScenarioStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DevToolsDebugHooksModule {
    @Provides
    fun providesRemoteConfigOverrides(store: RemoteConfigOverrideStore): RemoteConfigOverrides =
        RemoteConfigOverrides(store::get)

    @Provides
    @Singleton
    @DebugAppUpdateManager
    fun providesDebugAppUpdateManager(
        @ApplicationContext context: Context,
        store: UpdateScenarioStore,
    ): AppUpdateManager = DevToolsAppUpdate.create(context, store.current())
}
