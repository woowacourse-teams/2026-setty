package com.aksworns22.setty.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.UserRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String,
    val password: String,
    val isLoading: Boolean
)

enum class LoginEvent {
    SUCCESS, NETWORK_ERROR, UNKNOWN_ERROR, CREDENTIAL_ERROR,
}

class LoginViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(
            username = "",
            password = "",
            isLoading = false
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<LoginEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    fun login() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true)
            }
            runCatching {
                userRepository.login(_uiState.value.username, _uiState.value.password)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                _event.send(LoginEvent.SUCCESS)
            }.onFailure {
                _uiState.update { it.copy(isLoading = false) }
                _event.send(LoginEvent.UNKNOWN_ERROR)
            }
        }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update {
            it.copy(username = value)
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update {
            it.copy(password = value)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                LoginViewModel(appContainer.userRepository)
            }
        }
    }
}
