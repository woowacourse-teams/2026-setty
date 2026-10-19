package cloud.setty.setty.data

import android.content.Context
import cloud.setty.setty.BuildConfig
import cloud.setty.setty.di.NetworkInterceptorsEntryPoint
import cloud.setty.setty.network.AuthInterceptor
import cloud.setty.setty.network.BasePathInterceptor
import cloud.setty.setty.network.SettyNetworkApi
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AppContainer(
    private val context: Context
) {
    private val extraInterceptors =
        EntryPointAccessors.fromApplication(context, NetworkInterceptorsEntryPoint::class.java)
            .interceptors()
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tokenLocalDataSource =
        TokenLocalDataSource(context.applicationContext)

    private val authInterceptor =
        AuthInterceptor(tokenLocalDataSource)
    private val okHttpClient: OkHttpClient = OkHttpClient
        .Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addNetworkInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
            }
            addInterceptor(BasePathInterceptor(BuildConfig.BASE_URL))
            addInterceptor(authInterceptor)
            extraInterceptors.forEach(::addInterceptor)
        }
        .build()
    private val retrofit: Retrofit =
        Retrofit.Builder().client(okHttpClient).baseUrl(BuildConfig.BASE_URL).addConverterFactory(
            Json { ignoreUnknownKeys = true }.asConverterFactory(
                "application/json".toMediaType()
            )
        ).build()
    private val settyNetworkApi = retrofit.create(SettyNetworkApi::class.java)

    val userRepository = UserRepository(
        settyNetworkApi = settyNetworkApi,
        tokenLocalDataSource = tokenLocalDataSource,
    )

    val furnitureRepository = FurnitureRepository(
        settyNetworkApi = settyNetworkApi,
        listingImageReader = ListingImageReader(context.applicationContext),
    )

    val favoriteRepository = FavoriteRepository(
        settyNetworkApi = settyNetworkApi,
    )

    val orderRepository = OrderRepository(
        settyNetworkApi = settyNetworkApi,
    )

    val paymentRepository = PaymentRepository(
        settyNetworkApi = settyNetworkApi,
    )

    val deviceTokenRepository = DeviceTokenRepository(
        settyNetworkApi = settyNetworkApi,
    )

    val notificationRepository = NotificationRepository(
        settyNetworkApi = settyNetworkApi,
    )

    val dongRepository = DongRepository(
        settyNetworkApi = settyNetworkApi,
    )

    val keywordRepository = KeywordRepository(
        settyNetworkApi = settyNetworkApi,
    )
}
