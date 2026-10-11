package com.aksworns22.setty.feature.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.NotificationRepository
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.isUnauthorized
import com.aksworns22.setty.feature.common.toLoadError
import com.aksworns22.setty.feature.common.toUserMessage
import com.aksworns22.setty.model.ListingNotification
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationListUiState(
    val notifications: LoadUiState<List<ListingNotification>> = LoadUiState.Loading,
    val isRefreshing: Boolean = false,
)

sealed interface NotificationListEvent {
    data class Message(val message: String) : NotificationListEvent

    data object RequireLogin : NotificationListEvent
}

class NotificationListViewModel(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationListUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<NotificationListEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    fun load(reset: Boolean = false) {
        if (reset || _uiState.value.notifications !is LoadUiState.Success) {
            _uiState.value = NotificationListUiState()
        }
        fetch()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        fetch()
    }

    private fun fetch() {
        viewModelScope.launch {
            runCatching {
                notificationRepository.getNotifications()
            }.onSuccess { notifications ->
                _uiState.update {
                    it.copy(notifications = LoadUiState.Success(notifications), isRefreshing = false)
                }
            }.onFailure { throwable ->
                val hasNotifications = _uiState.value.notifications is LoadUiState.Success
                _uiState.update {
                    it.copy(
                        notifications = if (hasNotifications) it.notifications else throwable.toLoadError(),
                        isRefreshing = false,
                    )
                }
                if (hasNotifications) _event.send(throwable.toEvent())
            }
        }
    }

    private fun Throwable.toEvent(): NotificationListEvent =
        if (isUnauthorized) NotificationListEvent.RequireLogin else NotificationListEvent.Message(toUserMessage())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                NotificationListViewModel(appContainer.notificationRepository)
            }
        }
    }
}
