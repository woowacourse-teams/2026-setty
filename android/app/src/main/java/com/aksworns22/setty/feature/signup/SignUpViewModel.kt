package com.aksworns22.setty.feature.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.UserRepository
import com.aksworns22.setty.network.ApiException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class SignUpUiState(
    val loginId: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val isLoading: Boolean = false,
) {
    val loginIdError: SignUpFieldError?
        get() = when {
            loginId.isEmpty() -> null
            !LOGIN_ID_REGEX.matches(loginId) -> SignUpFieldError.INVALID_LOGIN_ID
            else -> null
        }

    val passwordError: SignUpFieldError?
        get() = when {
            password.isEmpty() -> null
            password.length !in PASSWORD_LENGTH -> SignUpFieldError.INVALID_PASSWORD_LENGTH
            else -> null
        }

    val passwordConfirmError: SignUpFieldError?
        get() = when {
            passwordConfirm.isEmpty() -> null
            passwordConfirm != password -> SignUpFieldError.PASSWORD_MISMATCH
            else -> null
        }

    val phoneNumberError: SignUpFieldError?
        get() = when {
            phoneNumber.isEmpty() -> null
            !PHONE_NUMBER_REGEX.matches(phoneNumber) -> SignUpFieldError.INVALID_PHONE_NUMBER
            else -> null
        }

    val addressError: SignUpFieldError?
        get() = when {
            address.length > ADDRESS_MAX_LENGTH -> SignUpFieldError.ADDRESS_TOO_LONG
            else -> null
        }

    val canSubmit: Boolean
        get() = !isLoading &&
                listOf(loginId, password, passwordConfirm, phoneNumber).all { it.isNotEmpty() } &&
                address.isNotBlank() &&
                listOf(
                    loginIdError,
                    passwordError,
                    passwordConfirmError,
                    phoneNumberError,
                    addressError,
                ).all { it == null }

    companion object {
        private val LOGIN_ID_REGEX = Regex("^[a-z0-9]{4,20}$")
        private val PHONE_NUMBER_REGEX = Regex("^010-\\d{4}-\\d{4}$")
        private val PASSWORD_LENGTH = 8..64
        const val ADDRESS_MAX_LENGTH = 200
    }
}

enum class SignUpFieldError {
    INVALID_LOGIN_ID,
    INVALID_PASSWORD_LENGTH,
    PASSWORD_MISMATCH,
    INVALID_PHONE_NUMBER,
    ADDRESS_TOO_LONG,
}

enum class SignUpEvent {
    SUCCESS, DUPLICATE_LOGIN_ID, INVALID_REQUEST, NETWORK_ERROR, UNKNOWN_ERROR,
}

class SignUpViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<SignUpEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    fun signUp() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                userRepository.signUp(
                    loginId = state.loginId,
                    password = state.password,
                    phoneNumber = state.phoneNumber,
                    address = state.address.trim(),
                )
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                _event.send(SignUpEvent.SUCCESS)
            }.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false) }
                _event.send(throwable.toSignUpEvent())
            }
        }
    }

    fun onLoginIdChanged(value: String) {
        _uiState.update { it.copy(loginId = value) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onPasswordConfirmChanged(value: String) {
        _uiState.update { it.copy(passwordConfirm = value) }
    }

    fun onPhoneNumberChanged(value: String) {
        _uiState.update { it.copy(phoneNumber = formatPhoneNumber(value)) }
    }

    fun onAddressChanged(value: String) {
        _uiState.update { it.copy(address = value) }
    }

    private fun Throwable.toSignUpEvent(): SignUpEvent = when (this) {
        is ApiException -> when (code) {
            "DUPLICATE_LOGIN_ID" -> SignUpEvent.DUPLICATE_LOGIN_ID
            "INVALID_REQUEST" -> SignUpEvent.INVALID_REQUEST
            else -> SignUpEvent.UNKNOWN_ERROR
        }

        is IOException -> SignUpEvent.NETWORK_ERROR
        else -> SignUpEvent.UNKNOWN_ERROR
    }

    companion object {
        private const val PHONE_NUMBER_MAX_DIGITS = 11

        fun formatPhoneNumber(value: String): String {
            val digits = value.filter(Char::isDigit).take(PHONE_NUMBER_MAX_DIGITS)
            return when {
                digits.length <= 3 -> digits
                digits.length <= 7 -> "${digits.take(3)}-${digits.drop(3)}"
                else -> "${digits.take(3)}-${digits.substring(3, 7)}-${digits.drop(7)}"
            }
        }

        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                SignUpViewModel(appContainer.userRepository)
            }
        }
    }
}
