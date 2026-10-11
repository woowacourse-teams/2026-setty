package com.aksworns22.setty.feature.keyword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.KeywordRepository
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.isUnauthorized
import com.aksworns22.setty.feature.common.toLoadError
import com.aksworns22.setty.feature.common.toUserMessage
import com.aksworns22.setty.model.Keyword
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeywordUiState(
    val keywords: LoadUiState<List<Keyword>> = LoadUiState.Loading,
    val input: String = "",
    val isSubmitting: Boolean = false,
)

sealed interface KeywordEvent {
    data class Message(val message: String) : KeywordEvent

    data object RequireLogin : KeywordEvent
}

class KeywordViewModel(
    private val keywordRepository: KeywordRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeywordUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<KeywordEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    fun load(reset: Boolean = false) {
        if (reset || _uiState.value.keywords !is LoadUiState.Success) {
            _uiState.value = KeywordUiState()
        }
        fetch()
    }

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun add() {
        val keyword = _uiState.value.input.trim()
        if (keyword.isEmpty() || _uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            runCatching {
                keywordRepository.add(keyword)
            }.onSuccess {
                _uiState.update { it.copy(input = "", isSubmitting = false) }
                fetch()
            }.onFailure { throwable ->
                _uiState.update { it.copy(isSubmitting = false) }
                _event.send(throwable.toEvent())
            }
        }
    }

    fun remove(keyword: Keyword) {
        val previous = (_uiState.value.keywords as? LoadUiState.Success)?.data ?: return
        _uiState.update { it.copy(keywords = LoadUiState.Success(previous - keyword)) }
        viewModelScope.launch {
            runCatching {
                keywordRepository.remove(keyword.id)
            }.onFailure { throwable ->
                _uiState.update { it.copy(keywords = LoadUiState.Success(previous)) }
                _event.send(throwable.toEvent())
            }
        }
    }

    private fun fetch() {
        viewModelScope.launch {
            runCatching {
                keywordRepository.getKeywords()
            }.onSuccess { keywords ->
                _uiState.update { it.copy(keywords = LoadUiState.Success(keywords)) }
            }.onFailure { throwable ->
                val hasKeywords = _uiState.value.keywords is LoadUiState.Success
                if (hasKeywords) {
                    _event.send(throwable.toEvent())
                } else {
                    _uiState.update { it.copy(keywords = throwable.toLoadError()) }
                }
            }
        }
    }

    private fun Throwable.toEvent(): KeywordEvent =
        if (isUnauthorized) KeywordEvent.RequireLogin else KeywordEvent.Message(toUserMessage())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                KeywordViewModel(appContainer.keywordRepository)
            }
        }
    }
}
