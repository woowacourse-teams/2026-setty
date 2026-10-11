package com.aksworns22.setty.feature.mylistings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.FurnitureRepository
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.isUnauthorized
import com.aksworns22.setty.feature.common.toLoadError
import com.aksworns22.setty.feature.common.toUserMessage
import com.aksworns22.setty.model.MyFurniture
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyListingsUiState(
    val furnitures: LoadUiState<List<MyFurniture>> = LoadUiState.Loading,
    val isRefreshing: Boolean = false,
    val deletingId: Long? = null,
)

sealed interface MyListingsEvent {
    data class Message(val message: String) : MyListingsEvent

    data object RequireLogin : MyListingsEvent
}

class MyListingsViewModel(
    private val furnitureRepository: FurnitureRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyListingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<MyListingsEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    /**
     * 화면에 들어올 때마다 다시 불러온다. 이미 보여주는 목록이 있으면 로딩을 띄우지 않는다.
     * ViewModel이 Activity 범위라 새로 연 화면이면 [reset]으로 이전 화면(다른 계정일 수 있다)의 목록을 버린다.
     */
    fun load(reset: Boolean = false) {
        if (reset || _uiState.value.furnitures !is LoadUiState.Success) {
            _uiState.value = MyListingsUiState()
        }
        fetch()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        fetch()
    }

    fun delete(listingId: Long) {
        if (_uiState.value.deletingId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = listingId) }
            runCatching {
                furnitureRepository.deleteListing(listingId)
            }.onSuccess {
                _uiState.update { state ->
                    val furnitures = (state.furnitures as? LoadUiState.Success)?.data.orEmpty()
                    state.copy(
                        furnitures = LoadUiState.Success(furnitures.filterNot { it.id == listingId }),
                        deletingId = null,
                    )
                }
                _event.send(MyListingsEvent.Message("가구를 삭제했습니다"))
            }.onFailure { throwable ->
                _uiState.update { it.copy(deletingId = null) }
                _event.send(throwable.toEvent())
            }
        }
    }

    private fun fetch() {
        viewModelScope.launch {
            runCatching {
                furnitureRepository.getMyFurniture()
            }.onSuccess { furnitures ->
                _uiState.update {
                    it.copy(furnitures = LoadUiState.Success(furnitures), isRefreshing = false)
                }
            }.onFailure { throwable ->
                val hasFurnitures = _uiState.value.furnitures is LoadUiState.Success
                _uiState.update {
                    it.copy(
                        furnitures = if (hasFurnitures) it.furnitures else throwable.toLoadError(),
                        isRefreshing = false,
                    )
                }
                if (hasFurnitures) _event.send(throwable.toEvent())
            }
        }
    }

    private fun Throwable.toEvent(): MyListingsEvent =
        if (isUnauthorized) MyListingsEvent.RequireLogin else MyListingsEvent.Message(toUserMessage())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                MyListingsViewModel(appContainer.furnitureRepository)
            }
        }
    }
}
