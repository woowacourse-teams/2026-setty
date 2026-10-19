package cloud.setty.setty.devtools.di

import cloud.setty.setty.devtools.scenario.FailureScenarioInterceptor
import cloud.setty.setty.devtools.scenario.MockScenarioStore
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
