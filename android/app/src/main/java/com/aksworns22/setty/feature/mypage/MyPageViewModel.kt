package com.aksworns22.setty.feature.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.UserRepository
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.toLoadError
import com.aksworns22.setty.model.Member
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyPageUiState(
    val member: LoadUiState<Member> = LoadUiState.Loading,
    val isLoggingOut: Boolean = false,
)

enum class MyPageEvent {
    LOGGED_OUT,
}

class MyPageViewModel(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<MyPageEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    /**
     * 화면에 들어올 때마다 회원 정보를 다시 불러온다. 이미 보여주는 정보가 있으면 로딩을 띄우지 않는다.
     * ViewModel이 Activity 범위라 새로 연 화면이면 [reset]으로 이전 계정의 정보를 버린다.
     */
    fun load(reset: Boolean = false) {
        viewModelScope.launch {
            if (reset || _uiState.value.member !is LoadUiState.Success) {
                _uiState.update { it.copy(member = LoadUiState.Loading) }
            }
            runCatching {
                userRepository.getMe()
            }.onSuccess { member ->
                _uiState.update { it.copy(member = LoadUiState.Success(member)) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(member = throwable.toLoadError()) }
            }
        }
    }

    fun logout() {
        if (_uiState.value.isLoggingOut) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }
            userRepository.logout()
            _uiState.value = MyPageUiState()
            _event.send(MyPageEvent.LOGGED_OUT)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                MyPageViewModel(appContainer.userRepository)
            }
        }
    }
}
