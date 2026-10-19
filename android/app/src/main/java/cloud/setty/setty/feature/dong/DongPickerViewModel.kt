package cloud.setty.setty.feature.dong

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cloud.setty.setty.SettyApp
import cloud.setty.setty.data.DongRepository
import cloud.setty.setty.feature.common.LoadUiState
import cloud.setty.setty.feature.common.toLoadError
import cloud.setty.setty.model.Dong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 구 → 동 순서로 고르거나 이름으로 찾을 수 있게 정리한 서울 행정동 목록. */
class DongCatalog(dongs: List<Dong>) {
    private val dongsByCode = dongs.associateBy { it.code }
    private val dongsByDistrict = dongs.groupBy { it.district }
        .mapValues { (_, dongs) -> dongs.sortedBy { it.name } }

    val districts: List<String> = dongsByDistrict.keys.sorted()

    fun dongsIn(district: String): List<Dong> = dongsByDistrict[district].orEmpty()

    fun find(code: String?): Dong? = code?.let(dongsByCode::get)

    /** 동 이름이나 "구 동" 이름에 검색어가 들어간 동을 찾는다. 띄어쓰기는 무시한다. */
    fun search(query: String): List<Dong> {
        val keyword = query.filterNot(Char::isWhitespace)
        if (keyword.isEmpty()) return emptyList()
        return districts.flatMap(::dongsIn).filter { dong ->
            (dong.district + dong.name).contains(keyword)
        }
    }
}

class DongPickerViewModel(
    private val dongRepository: DongRepository,
) : ViewModel() {

    private val _catalog = MutableStateFlow<LoadUiState<DongCatalog>>(LoadUiState.Loading)
    val catalog = _catalog.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _catalog.value = LoadUiState.Loading
            runCatching {
                dongRepository.getDongs()
            }.onSuccess { dongs ->
                _catalog.value = LoadUiState.Success(DongCatalog(dongs))
            }.onFailure { throwable ->
                _catalog.value = throwable.toLoadError()
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                DongPickerViewModel(appContainer.dongRepository)
            }
        }
    }
}
