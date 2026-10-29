package com.setty.android.di

import javax.inject.Qualifier

// 개발자 도구가 넣는 Fake와 앱이 실제로 쓰는 AppUpdateManager를 구분한다
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugAppUpdateManager
