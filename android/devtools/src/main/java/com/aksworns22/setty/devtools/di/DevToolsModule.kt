package com.aksworns22.setty.devtools.di

import com.aksworns22.setty.devtools.scenario.FailureScenarioInterceptor
import com.aksworns22.setty.devtools.scenario.MockScenarioStore
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
