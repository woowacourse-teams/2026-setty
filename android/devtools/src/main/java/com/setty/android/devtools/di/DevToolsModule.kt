package com.setty.android.devtools.di

import com.setty.android.devtools.scenario.FailureScenarioInterceptor
import com.setty.android.devtools.scenario.MockScenarioStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor

@Module
@InstallIn(SingletonComponent::class)
internal object DevToolsModule {
    @Provides
    @IntoSet
    fun providesFailureScenarioInterceptor(store: MockScenarioStore): Interceptor =
        FailureScenarioInterceptor(isEnabled = store::isEnabled)
}
