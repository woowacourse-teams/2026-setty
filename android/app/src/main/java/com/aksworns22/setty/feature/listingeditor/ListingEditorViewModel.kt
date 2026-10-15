package com.aksworns22.setty.feature.listingeditor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.core.model.FurnitureCategory
import com.aksworns22.setty.data.FurnitureRepository
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.isMemberDongRequired
import com.aksworns22.setty.feature.common.isUnauthorized
import com.aksworns22.setty.feature.common.toLoadError
import com.aksworns22.setty.feature.common.toUserMessage
import com.aksworns22.setty.model.ListingDraft
import com.aksworns22.setty.model.ListingImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListingEditorUiState(
    // null이면 새 매물 등록
    val listingId: Long? = null,
    // 수정할 매물을 불러오는 상태. 등록일 때는 바로 Success
    val loadState: LoadUiState<Unit> = LoadUiState.Success(Unit),
    val title: String = "",
    val description: String = "",
    // 숫자만 저장한다
    val price: String = "",
    val category: FurnitureCategory? = null,
    val conditionGrade: String? = null,
    val widthCm: String = "",
    val depthCm: String = "",
    val heightCm: String = "",
    // 수정 시 유지할 기존 이미지. 이 순서대로 재정렬된다
    val retainedImages: List<ListingImage> = emptyList(),
    val newImages: List<Uri> = emptyList(),
    val isSubmitting: Boolean = false,
) {
    val isEditing: Boolean
        get() = listingId != null

    val imageCount: Int
        get() = retainedImages.size + newImages.size

    val remainingImageSlots: Int
        get() = (MAX_IMAGE_COUNT - imageCount).coerceAtLeast(0)

    val titleError: Boolean
        get() = title.length > TITLE_MAX_LENGTH

    val descriptionError: Boolean
        get() = description.length > DESCRIPTION_MAX_LENGTH

    val priceError: Boolean
        get() = price.isNotEmpty() && (price.toLongOrNull() ?: Long.MAX_VALUE) > PRICE_MAX

    val widthError: Boolean get() = isDimensionInvalid(widthCm)
    val depthError: Boolean get() = isDimensionInvalid(depthCm)
    val heightError: Boolean get() = isDimensionInvalid(heightCm)

    val canSubmit: Boolean
        get() = !isSubmitting &&
                loadState is LoadUiState.Success &&
                title.isNotBlank() && !titleError &&
                description.isNotBlank() && !descriptionError &&
                price.isNotEmpty() && !priceError &&
                category != null && conditionGrade != null &&
                listOf(widthCm, depthCm, heightCm).all { it.isNotEmpty() } &&
                !widthError && !depthError && !heightError &&
                imageCount in 1..MAX_IMAGE_COUNT

    fun toDraft(): ListingDraft = ListingDraft(
        title = title.trim(),
        description = description.trim(),
        price = price.toInt(),
        category = requireNotNull(category),
        conditionGrade = requireNotNull(conditionGrade),
        widthCm = widthCm.toInt(),
        depthCm = depthCm.toInt(),
        heightCm = heightCm.toInt(),
    )

    private fun isDimensionInvalid(value: String): Boolean =
        value.isNotEmpty() && (value.toIntOrNull() ?: Int.MAX_VALUE) !in DIMENSION_RANGE

    companion object {
        const val MAX_IMAGE_COUNT = 5
        const val TITLE_MAX_LENGTH = 100
        const val DESCRIPTION_MAX_LENGTH = 1_000
        const val PRICE_MAX = 100_000_000L
        val DIMENSION_RANGE = 1..1_000
    }
}

sealed interface ListingEditorEvent {
    data class Saved(val listingId: Long) : ListingEditorEvent

    data object RequireLogin : ListingEditorEvent

    // 판매자 행정동이 없어 등록할 수 없다
    data object RequireDong : ListingEditorEvent

    data class Failed(val message: String) : ListingEditorEvent
}

class ListingEditorViewModel(
    private val furnitureRepository: FurnitureRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ListingEditorUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<ListingEditorEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private var loadJob: Job? = null

    /** [start]가 호출됐는지. 프로세스 종료 후에도 [SavedStateHandle]로 복원된다. */
    val isStarted: Boolean
        get() = savedStateHandle[KEY_STARTED] ?: false

    init {
        if (isStarted) restore()
        // 입력 중인 폼을 저장해 프로세스가 종료돼도 복원한다. 사진은 권한 문제로 저장하지 않는다
        viewModelScope.launch {
            uiState.collect { state ->
                if (state.loadState is LoadUiState.Success) saveForm(state)
            }
        }
    }

    /** 편집 화면을 새로 시작한다. [listingId]가 있으면 기존 매물을 불러와 폼을 채운다. */
    fun start(listingId: Long?) {
        loadJob?.cancel()
        FORM_KEYS.forEach { savedStateHandle.remove<Any>(it) }
        savedStateHandle[KEY_STARTED] = true
        savedStateHandle[KEY_LISTING_ID] = listingId
        _uiState.value = ListingEditorUiState(listingId = listingId, loadState = initialLoadState(listingId))
        if (listingId != null) load(listingId)
    }

    fun retry() {
        _uiState.value.listingId?.let(::load)
    }

    /** 프로세스 종료 전 입력을 복원한다. 수정 중이었다면 기존 사진만 다시 불러온다. */
    private fun restore() {
        val listingId: Long? = savedStateHandle[KEY_LISTING_ID]
        val hasSavedForm = savedStateHandle.contains(KEY_TITLE)
        _uiState.value = ListingEditorUiState(
            listingId = listingId,
            loadState = initialLoadState(listingId),
            title = savedStateHandle[KEY_TITLE] ?: "",
            description = savedStateHandle[KEY_DESCRIPTION] ?: "",
            price = savedStateHandle[KEY_PRICE] ?: "",
            category = savedStateHandle.get<String>(KEY_CATEGORY)?.let(FurnitureCategory::valueOf),
            conditionGrade = savedStateHandle[KEY_CONDITION_GRADE],
            widthCm = savedStateHandle[KEY_WIDTH_CM] ?: "",
            depthCm = savedStateHandle[KEY_DEPTH_CM] ?: "",
            heightCm = savedStateHandle[KEY_HEIGHT_CM] ?: "",
        )
        if (listingId != null) load(listingId, keepForm = hasSavedForm)
    }

    // 수정할 매물을 받기 전의 빈 폼이 저장되지 않도록 처음부터 Loading으로 둔다
    private fun initialLoadState(listingId: Long?): LoadUiState<Unit> =
        if (listingId != null) LoadUiState.Loading else LoadUiState.Success(Unit)

    private fun saveForm(state: ListingEditorUiState) {
        savedStateHandle[KEY_TITLE] = state.title
        savedStateHandle[KEY_DESCRIPTION] = state.description
        savedStateHandle[KEY_PRICE] = state.price
        savedStateHandle[KEY_CATEGORY] = state.category?.name
        savedStateHandle[KEY_CONDITION_GRADE] = state.conditionGrade
        savedStateHandle[KEY_WIDTH_CM] = state.widthCm
        savedStateHandle[KEY_DEPTH_CM] = state.depthCm
        savedStateHandle[KEY_HEIGHT_CM] = state.heightCm
    }

    /** [keepForm]이면 복원한 입력을 유지하고 기존 사진만 채운다. */
    private fun load(listingId: Long, keepForm: Boolean = false) {
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loadState = LoadUiState.Loading) }
            runCatching {
                furnitureRepository.getEditableListing(listingId)
            }.onSuccess { listing ->
                val draft = listing.draft
                _uiState.update {
                    if (keepForm) {
                        it.copy(loadState = LoadUiState.Success(Unit), retainedImages = listing.images)
                    } else {
                        it.copy(
                            loadState = LoadUiState.Success(Unit),
                            title = draft.title,
                            description = draft.description,
                            price = draft.price.toString(),
                            category = draft.category,
                            conditionGrade = draft.conditionGrade,
                            widthCm = draft.widthCm.toString(),
                            depthCm = draft.depthCm.toString(),
                            heightCm = draft.heightCm.toString(),
                            retainedImages = listing.images,
                        )
                    }
                }
            }.onFailure { throwable ->
                _uiState.update { it.copy(loadState = throwable.toLoadError()) }
            }
        }
    }

    fun onTitleChanged(value: String) = _uiState.update { it.copy(title = value) }

    fun onDescriptionChanged(value: String) = _uiState.update { it.copy(description = value) }

    fun onPriceChanged(value: String) = _uiState.update { it.copy(price = value.digits(PRICE_MAX_DIGITS)) }

    fun onCategorySelected(category: FurnitureCategory) = _uiState.update { it.copy(category = category) }

    fun onConditionGradeSelected(grade: String) = _uiState.update { it.copy(conditionGrade = grade) }

    fun onWidthChanged(value: String) = _uiState.update { it.copy(widthCm = value.digits(DIMENSION_MAX_DIGITS)) }

    fun onDepthChanged(value: String) = _uiState.update { it.copy(depthCm = value.digits(DIMENSION_MAX_DIGITS)) }

    fun onHeightChanged(value: String) = _uiState.update { it.copy(heightCm = value.digits(DIMENSION_MAX_DIGITS)) }

    /** 남은 자리만큼만 추가한다. 이미 고른 사진은 다시 추가하지 않는다. */
    fun onImagesPicked(uris: List<Uri>) = _uiState.update { state ->
        val added = uris.filterNot { it in state.newImages }.take(state.remainingImageSlots)
        state.copy(newImages = state.newImages + added)
    }

    fun onRetainedImageRemoved(imageId: Long) = _uiState.update { state ->
        state.copy(retainedImages = state.retainedImages.filterNot { it.id == imageId })
    }

    fun onNewImageRemoved(uri: Uri) = _uiState.update { state ->
        state.copy(newImages = state.newImages - uri)
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            runCatching {
                val draft = state.toDraft()
                val listingId = state.listingId
                if (listingId == null) {
                    furnitureRepository.createListing(draft, state.newImages)
                } else {
                    furnitureRepository.updateListing(
                        listingId = listingId,
                        draft = draft,
                        retainedImageIds = state.retainedImages.map { it.id },
                        newImages = state.newImages,
                    )
                    listingId
                }
            }.onSuccess { listingId ->
                _uiState.update { it.copy(isSubmitting = false) }
                _event.send(ListingEditorEvent.Saved(listingId))
            }.onFailure { throwable ->
                _uiState.update { it.copy(isSubmitting = false) }
                _event.send(
                    when {
                        throwable.isUnauthorized -> ListingEditorEvent.RequireLogin
                        throwable.isMemberDongRequired -> ListingEditorEvent.RequireDong
                        else -> ListingEditorEvent.Failed(throwable.toUserMessage("가구를 저장하지 못했습니다"))
                    }
                )
            }
        }
    }

    private fun String.digits(maxLength: Int): String = filter(Char::isDigit).take(maxLength)

    companion object {
        // 최대 금액(1억)보다 한 자리 더 받아 범위 초과를 알려준다
        private const val PRICE_MAX_DIGITS = 10
        private const val DIMENSION_MAX_DIGITS = 5

        private const val KEY_STARTED = "listing_editor_started"
        private const val KEY_LISTING_ID = "listing_editor_listing_id"
        private const val KEY_TITLE = "listing_editor_title"
        private const val KEY_DESCRIPTION = "listing_editor_description"
        private const val KEY_PRICE = "listing_editor_price"
        private const val KEY_CATEGORY = "listing_editor_category"
        private const val KEY_CONDITION_GRADE = "listing_editor_condition_grade"
        private const val KEY_WIDTH_CM = "listing_editor_width_cm"
        private const val KEY_DEPTH_CM = "listing_editor_depth_cm"
        private const val KEY_HEIGHT_CM = "listing_editor_height_cm"
        private val FORM_KEYS = listOf(
            KEY_TITLE, KEY_DESCRIPTION, KEY_PRICE, KEY_CATEGORY, KEY_CONDITION_GRADE,
            KEY_WIDTH_CM, KEY_DEPTH_CM, KEY_HEIGHT_CM,
        )

        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                ListingEditorViewModel(appContainer.furnitureRepository, createSavedStateHandle())
            }
        }
    }
}
