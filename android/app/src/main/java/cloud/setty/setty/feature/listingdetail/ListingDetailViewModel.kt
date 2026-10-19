package cloud.setty.setty.feature.listingdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cloud.setty.setty.SettyApp
import cloud.setty.setty.data.FavoriteRepository
import cloud.setty.setty.data.FurnitureRepository
import cloud.setty.setty.core.model.FurnitureDetail
import cloud.setty.setty.feature.common.isUnauthorized
import cloud.setty.setty.feature.common.toUserMessage
import cloud.setty.setty.network.ApiException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface ListingDetailUiState {
    data object Loading : ListingDetailUiState
    data class Success(val listing: FurnitureDetail) : ListingDetailUiState
    data class Error(val reason: ListingDetailError) : ListingDetailUiState
}

enum class ListingDetailError {
    NOT_FOUND, NETWORK_ERROR, UNKNOWN_ERROR,
}

data class FavoriteUiState(
    // 찜 여부를 아직 모르면 null
    val isFavorited: Boolean? = null,
    val isUpdating: Boolean = false,
)

sealed interface ListingDetailEvent {
    data class Message(val message: String) : ListingDetailEvent

    data object RequireLogin : ListingDetailEvent
}

class ListingDetailViewModel(
    private val listingId: Long,
    private val furnitureRepository: FurnitureRepository,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListingDetailUiState>(ListingDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _favorite = MutableStateFlow(FavoriteUiState())
    val favorite = _favorite.asStateFlow()

    private val _event = Channel<ListingDetailEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    /**
     * 찜 여부를 불러온다. 다른 화면에서 찜을 바꿀 수 있어 화면에 들어올 때마다 부른다.
     * 부가 정보라 실패해도 알리지 않고 토글만 숨긴다.
     */
    fun loadFavorite() {
        if (_favorite.value.isUpdating) return
        viewModelScope.launch {
            runCatching { favoriteRepository.isFavorited(listingId) }
                .onSuccess { isFavorited -> _favorite.update { it.copy(isFavorited = isFavorited) } }
        }
    }

    fun toggleFavorite() {
        val current = _favorite.value
        val isFavorited = current.isFavorited ?: return
        if (current.isUpdating) return
        // 먼저 바꿔 보여주고 실패하면 되돌린다
        _favorite.value = FavoriteUiState(isFavorited = !isFavorited, isUpdating = true)
        viewModelScope.launch {
            runCatching {
                if (isFavorited) favoriteRepository.remove(listingId) else favoriteRepository.add(listingId)
            }.onSuccess {
                _favorite.update { it.copy(isUpdating = false) }
                _event.send(ListingDetailEvent.Message(if (isFavorited) "찜을 해제했습니다" else "찜했습니다"))
            }.onFailure { throwable ->
                _favorite.value = FavoriteUiState(isFavorited = isFavorited)
                _event.send(
                    if (throwable.isUnauthorized) {
                        ListingDetailEvent.RequireLogin
                    } else {
                        ListingDetailEvent.Message(throwable.toUserMessage())
                    }
                )
            }
        }
    }

    /** 화면에 들어올 때마다 부른다. 이미 보여주는 매물이 있으면 로딩 없이 최신 정보로 바꾼다. */
    fun refresh() {
        if (_uiState.value !is ListingDetailUiState.Success) {
            load()
            return
        }
        viewModelScope.launch {
            runCatching { furnitureRepository.getFurnitureDetail(listingId) }
                .onSuccess { listing -> _uiState.value = ListingDetailUiState.Success(listing) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ListingDetailUiState.Loading
            runCatching {
                furnitureRepository.getFurnitureDetail(listingId)
            }.onSuccess { listing ->
                _uiState.value = ListingDetailUiState.Success(listing)
            }.onFailure { throwable ->
                _uiState.value = ListingDetailUiState.Error(throwable.toListingDetailError())
            }
        }
    }

    private fun Throwable.toListingDetailError(): ListingDetailError = when (this) {
        is ApiException -> when (code) {
            "LISTING_NOT_FOUND" -> ListingDetailError.NOT_FOUND
            else -> ListingDetailError.UNKNOWN_ERROR
        }

        is IOException -> ListingDetailError.NETWORK_ERROR
        else -> ListingDetailError.UNKNOWN_ERROR
    }

    companion object {
        fun factory(listingId: Long) = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                ListingDetailViewModel(
                    listingId = listingId,
                    furnitureRepository = appContainer.furnitureRepository,
                    favoriteRepository = appContainer.favoriteRepository,
                )
            }
        }
    }
}
