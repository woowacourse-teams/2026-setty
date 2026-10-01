package com.aksworns22.setty.feature.listingdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.ListingRepository
import com.aksworns22.setty.model.ListingDetail
import com.aksworns22.setty.network.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface ListingDetailUiState {
    data object Loading : ListingDetailUiState
    data class Success(val listing: ListingDetail) : ListingDetailUiState
    data class Error(val reason: ListingDetailError) : ListingDetailUiState
}

enum class ListingDetailError {
    NOT_FOUND, NETWORK_ERROR, UNKNOWN_ERROR,
}

class ListingDetailViewModel(
    private val listingId: Long,
    private val listingRepository: ListingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListingDetailUiState>(ListingDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ListingDetailUiState.Loading
            runCatching {
                listingRepository.getListingDetail(listingId)
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
                ListingDetailViewModel(listingId, appContainer.listingRepository)
            }
        }
    }
}
