package com.setty.android.feature.common

/** 서버에서 한 번에 불러와 보여주는 화면의 상태. */
sealed interface LoadUiState<out T> {
    data object Loading : LoadUiState<Nothing>

    data class Success<T>(val data: T) : LoadUiState<T>

    data class Error(val message: String, val isUnauthorized: Boolean) : LoadUiState<Nothing>
}

fun Throwable.toLoadError(): LoadUiState.Error = LoadUiState.Error(toUserMessage(), isUnauthorized)
