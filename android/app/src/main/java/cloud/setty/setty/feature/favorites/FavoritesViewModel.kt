package cloud.setty.setty.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cloud.setty.setty.SettyApp
import cloud.setty.setty.core.model.FurnitureSummary
import cloud.setty.setty.data.FavoriteRepository
import cloud.setty.setty.feature.common.LoadUiState
import cloud.setty.setty.feature.common.isUnauthorized
import cloud.setty.setty.feature.common.toLoadError
import cloud.setty.setty.feature.common.toUserMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val furnitures: LoadUiState<List<FurnitureSummary>> = LoadUiState.Loading,
    val isRefreshing: Boolean = false,
)

sealed interface FavoritesEvent {
    data class Message(val message: String) : FavoritesEvent

    data object RequireLogin : FavoritesEvent
}

class FavoritesViewModel(
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<FavoritesEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    /**
     * 화면에 들어올 때마다 다시 불러온다. 이미 보여주는 목록이 있으면 로딩을 띄우지 않는다.
     * ViewModel이 Activity 범위라 새로 연 화면이면 [reset]으로 이전 화면(다른 계정일 수 있다)의 목록을 버린다.
     */
    fun load(reset: Boolean = false) {
        if (reset || _uiState.value.furnitures !is LoadUiState.Success) {
            _uiState.value = FavoritesUiState()
        }
        fetch()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        fetch()
    }

    /** 목록에서 먼저 빼고 실패하면 되돌린다. */
    fun remove(furniture: FurnitureSummary) {
        val previous = (_uiState.value.furnitures as? LoadUiState.Success)?.data ?: return
        _uiState.update { it.copy(furnitures = LoadUiState.Success(previous - furniture)) }
        viewModelScope.launch {
            runCatching {
                favoriteRepository.remove(furniture.id)
            }.onFailure { throwable ->
                _uiState.update { it.copy(furnitures = LoadUiState.Success(previous)) }
                _event.send(throwable.toEvent())
            }
        }
    }

    private fun fetch() {
        viewModelScope.launch {
            runCatching {
                favoriteRepository.getFavorites()
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

    private fun Throwable.toEvent(): FavoritesEvent =
        if (isUnauthorized) FavoritesEvent.RequireLogin else FavoritesEvent.Message(toUserMessage())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                FavoritesViewModel(appContainer.favoriteRepository)
            }
        }
    }
}
