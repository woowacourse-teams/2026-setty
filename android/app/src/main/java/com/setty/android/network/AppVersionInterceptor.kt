package com.setty.android.network

import com.setty.android.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

// 서버 로그로 사용자 기기에서 돌고 있는 앱 버전 분포를 볼 수 있게 한다
class AppVersionInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder()
            .header(HEADER_APP_VERSION_CODE, BuildConfig.VERSION_CODE.toString())
            .build()
    )

    private companion object {
        const val HEADER_APP_VERSION_CODE = "X-App-Version-Code"
    }
}
