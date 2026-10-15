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
import com.aksworns22.setty.feature.common.isMemberDongRequired
import com.aksworns22.setty.model.FurniturePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val furnitures: List<FurnitureSummary> = emptyList(),
    val selectedCategory: FurnitureCategory? = null,
    // 고른 배송비 예산. null이면 거르지 않고 전체를 한 번에 받는다
    val maxDeliveryFee: Int? = null,
    // 배송비로 거를 때 이어 받을 다음 페이지 커서. 마지막 페이지면 null
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    // 프로필 버튼에 보여줄 아이디 첫 글자. 회원 정보를 아직 못 받았으면 null
    val profileInitial: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasError: Boolean = false,
) {
    val hasMore: Boolean
        get() = maxDeliveryFee != null && nextCursor != null

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
    LOAD_MORE_ERROR,

    // 회원 행정동이 없어 배송비로 거를 수 없다
    REQUIRE_DONG,
}

class HomeViewModel(
    private val furnitureRepository: FurnitureRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<HomeEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    // 목록을 받는 요청. 필터를 바꾸면 이전 요청의 결과가 뒤늦게 덮어쓰지 않도록 취소한다
    private var listingsJob: Job? = null

    // 지금 보여주는 목록을 받은 배송비 예산. 다른 예산으로 받다 실패하면 필터를 이 값으로 되돌린다
    private var shownMaxDeliveryFee: Int? = null

    /**
     * 홈에 들어올 때마다 부른다. ViewModel이 Activity 범위라 다른 화면에서 가구를 등록·수정·삭제하고
     * 돌아와도 다시 만들어지지 않으므로, 이미 보여주는 목록이 있으면 로딩 없이 최신 목록으로 바꾼다.
     * 배송비로 거른 목록은 첫 페이지만 다시 받으면 이어 받은 페이지가 사라지므로 그대로 둔다.
     */
    fun onEnter() {
        val state = _uiState.value
        when {
            state.furnitures.isEmpty() -> loadListings()
            state.maxDeliveryFee == null -> fetchListings()
        }
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

    /** 배송비 예산을 바꾸고 첫 페이지부터 다시 받는다. null이면 필터를 해제한다. */
    fun onMaxDeliveryFeeSelected(maxDeliveryFee: Int?) {
        if (_uiState.value.maxDeliveryFee == maxDeliveryFee) return
        _uiState.update { it.copy(maxDeliveryFee = maxDeliveryFee, isLoading = true, hasError = false) }
        fetchListings()
    }

    /** 배송비로 거른 목록의 다음 페이지를 이어 받는다. */
    fun loadMore() {
        val state = _uiState.value
        val maxDeliveryFee = state.maxDeliveryFee ?: return
        val cursor = state.nextCursor ?: return
        if (state.isLoading || state.isRefreshing || state.isLoadingMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        listingsJob = viewModelScope.launch {
            runCatching {
                furnitureRepository.getFurnitureWithinDeliveryFee(maxDeliveryFee, cursor)
            }.onSuccess { page ->
                _uiState.update {
                    it.copy(
                        // 페이지 사이에 새 매물이 끼어도 같은 매물이 두 번 보이지 않게 한다
                        furnitures = (it.furnitures + page.items).distinctBy { listing -> listing.id },
                        nextCursor = page.nextCursor,
                        isLoadingMore = false,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                _uiState.update { it.copy(isLoadingMore = false) }
                _event.send(HomeEvent.LOAD_MORE_ERROR)
            }
        }
    }

    private fun fetchListings() {
        listingsJob?.cancel()
        val maxDeliveryFee = _uiState.value.maxDeliveryFee
        listingsJob = viewModelScope.launch {
            runCatching {
                if (maxDeliveryFee == null) {
                    FurniturePage(furnitureRepository.getFurniture(), nextCursor = null)
                } else {
                    furnitureRepository.getFurnitureWithinDeliveryFee(maxDeliveryFee, cursor = null)
                }
            }.onSuccess { page ->
                shownMaxDeliveryFee = maxDeliveryFee
                _uiState.update {
                    it.copy(
                        furnitures = page.items,
                        nextCursor = page.nextCursor,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        hasError = false,
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                if (throwable.isMemberDongRequired) {
                    _uiState.update {
                        it.copy(
                            maxDeliveryFee = shownMaxDeliveryFee,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                        )
                    }
                    _event.send(HomeEvent.REQUIRE_DONG)
                    return@onFailure
                }
                val hasListings = _uiState.value.furnitures.isNotEmpty()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        hasError = !hasListings,
                        maxDeliveryFee = if (hasListings) shownMaxDeliveryFee else it.maxDeliveryFee,
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
