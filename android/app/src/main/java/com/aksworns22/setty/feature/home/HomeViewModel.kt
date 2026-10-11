package com.aksworns22.setty.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.FurnitureRepository
import com.aksworns22.setty.data.UserRepository
import com.aksworns22.setty.core.model.FurnitureCategory
import com.aksworns22.setty.core.model.FurnitureSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val furnitures: List<FurnitureSummary> = emptyList(),
    val selectedCategory: FurnitureCategory? = null,
    // 프로필 버튼에 보여줄 아이디 첫 글자. 회원 정보를 아직 못 받았으면 null
    val profileInitial: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasError: Boolean = false,
) {
    val visibleFurnitures: List<FurnitureSummary>
        get() = if (selectedCategory == null) {
            furnitures
        } else {
            furnitures.filter { it.category == selectedCategory }
        }

    /** 불러온 판매 중 가구에서 제목에 [query]가 들어간 가구를 찾는다. 서버 검색 API가 없어 앱에서 거른다. */
    fun search(query: String): List<FurnitureSummary> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        return furnitures.filter { it.title.contains(keyword, ignoreCase = true) }
    }
}

enum class HomeEvent {
    REFRESH_ERROR,
}

class HomeViewModel(
    private val furnitureRepository: FurnitureRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<HomeEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    /**
     * 홈에 들어올 때마다 부른다. ViewModel이 Activity 범위라 다른 화면에서 가구를 등록·수정·삭제하고
     * 돌아와도 다시 만들어지지 않으므로, 이미 보여주는 목록이 있으면 로딩 없이 최신 목록으로 바꾼다.
     */
    fun onEnter() {
        if (_uiState.value.furnitures.isEmpty()) loadListings() else fetchListings()
        loadProfile()
    }

    /** 프로필은 부가 정보라 실패해도 알리지 않고 기본 아이콘을 유지한다. */
    private fun loadProfile() {
        viewModelScope.launch {
            runCatching { userRepository.getMe() }
                .onSuccess { member ->
                    _uiState.update { it.copy(profileInitial = member.loginId.take(1).uppercase()) }
                }
        }
    }

    fun loadListings() {
        _uiState.update { it.copy(isLoading = true, hasError = false) }
        fetchListings()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        fetchListings()
        loadProfile()
    }

    fun onCategorySelected(category: FurnitureCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    private fun fetchListings() {
        viewModelScope.launch {
            runCatching {
                furnitureRepository.getFurniture()
            }.onSuccess { listings ->
                _uiState.update {
                    it.copy(
                        furnitures = listings,
                        isLoading = false,
                        isRefreshing = false,
                        hasError = false,
                    )
                }
            }.onFailure {
                val hasListings = _uiState.value.furnitures.isNotEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        hasError = !hasListings,
                    )
                }
                if (hasListings) {
                    _event.send(HomeEvent.REFRESH_ERROR)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                HomeViewModel(appContainer.furnitureRepository, appContainer.userRepository)
            }
        }
    }
}
