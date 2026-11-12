package com.setty.android.feature.common

/** 서버에서 한 번에 불러와 보여주는 화면의 상태. */
sealed interface LoadUiState<out T> {
    data object Loading : LoadUiState<Nothing>

    data class Success<T>(val data: T) : LoadUiState<T>

    /** [isNotFound]면 대상이 없거나 삭제돼 다시 시도해도 소용이 없다. */
    data class Error(
        val message: String,
        val isUnauthorized: Boolean,
        val isNotFound: Boolean = false,
    ) : LoadUiState<Nothing>
}

fun Throwable.toLoadError(): LoadUiState.Error = LoadUiState.Error(toUserMessage(), isUnauthorized, isNotFound)
