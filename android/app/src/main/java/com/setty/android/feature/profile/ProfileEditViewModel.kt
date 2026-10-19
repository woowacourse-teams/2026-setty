package com.setty.android.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.setty.android.SettyApp
import com.setty.android.data.UserRepository
import com.setty.android.feature.common.LoadUiState
import com.setty.android.feature.common.isUnauthorized
import com.setty.android.feature.common.toLoadError
import com.setty.android.feature.common.toUserMessage
import com.setty.android.feature.signup.SignUpViewModel
import com.setty.android.model.Dong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileForm(
    val loginId: String,
    // 하이픈 없는 숫자만 저장한다
    val phoneNumber: String,
    val address: String,
    val dongCode: String?,
    // 서버에 저장된 행정동. 바뀌었을 때만 행정동 변경을 요청한다
    val savedDongCode: String? = dongCode,
) {
    val isPhoneNumberValid: Boolean
        get() = PHONE_NUMBER_REGEX.matches(phoneNumber)

    val isAddressTooLong: Boolean
        get() = address.length > ADDRESS_MAX_LENGTH

    val canSubmit: Boolean
        get() = isPhoneNumberValid && address.isNotBlank() && !isAddressTooLong && dongCode != null

    val isDongChanged: Boolean
        get() = dongCode != savedDongCode

    companion object {
        private val PHONE_NUMBER_REGEX = Regex("^010\\d{8}$")
        const val ADDRESS_MAX_LENGTH = 200
    }
}

data class ProfileEditUiState(
    val form: LoadUiState<ProfileForm> = LoadUiState.Loading,
    val isSaving: Boolean = false,
)

sealed interface ProfileEditEvent {
    data object Saved : ProfileEditEvent

    data object RequireLogin : ProfileEditEvent

    data class Failed(val message: String) : ProfileEditEvent
}

class ProfileEditViewModel(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileEditUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<ProfileEditEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    /** 서버의 현재 회원 정보로 입력 폼을 채운다. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = ProfileEditUiState()
            runCatching {
                userRepository.getMe()
            }.onSuccess { member ->
                val form = ProfileForm(
                    loginId = member.loginId,
                    phoneNumber = member.phoneNumber.filter(Char::isDigit),
                    address = member.address,
                    dongCode = member.dongCode,
                )
                _uiState.update { it.copy(form = LoadUiState.Success(form)) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(form = throwable.toLoadError()) }
            }
        }
    }

    fun onPhoneNumberChanged(value: String) = updateForm {
        it.copy(phoneNumber = value.filter(Char::isDigit).take(SignUpViewModel.PHONE_NUMBER_MAX_DIGITS))
    }

    fun onAddressChanged(value: String) = updateForm { it.copy(address = value) }

    fun onDongSelected(dong: Dong) = updateForm { it.copy(dongCode = dong.code) }

    fun save() {
        val state = _uiState.value
        val form = (state.form as? LoadUiState.Success)?.data ?: return
        if (!form.canSubmit || state.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                userRepository.updateMe(
                    phoneNumber = SignUpViewModel.formatPhoneNumber(form.phoneNumber),
                    address = form.address.trim(),
                )
                if (form.isDongChanged && form.dongCode != null) {
                    userRepository.updateDong(form.dongCode)
                    updateForm { it.copy(savedDongCode = form.dongCode) }
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false) }
                _event.send(ProfileEditEvent.Saved)
            }.onFailure { throwable ->
                _uiState.update { it.copy(isSaving = false) }
                _event.send(
                    if (throwable.isUnauthorized) {
                        ProfileEditEvent.RequireLogin
                    } else {
                        ProfileEditEvent.Failed(throwable.toUserMessage())
                    }
                )
            }
        }
    }

    private fun updateForm(transform: (ProfileForm) -> ProfileForm) {
        _uiState.update { state ->
            val form = state.form as? LoadUiState.Success ?: return@update state
            state.copy(form = LoadUiState.Success(transform(form.data)))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                ProfileEditViewModel(appContainer.userRepository)
            }
        }
    }
}
