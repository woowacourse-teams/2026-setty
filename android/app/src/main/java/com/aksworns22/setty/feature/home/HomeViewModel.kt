package com.aksworns22.setty.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.ListingRepository
import com.aksworns22.setty.model.Listing
import com.aksworns22.setty.model.ListingCategory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val listings: List<Listing> = emptyList(),
    val selectedCategory: ListingCategory? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasError: Boolean = false,
) {
    val visibleListings: List<Listing>
        get() = if (selectedCategory == null) {
            listings
        } else {
            listings.filter { it.category == selectedCategory }
        }
}

enum class HomeEvent {
    REFRESH_ERROR,
}

class HomeViewModel(
    private val listingRepository: ListingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<HomeEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    init {
        loadListings()
    }

    fun loadListings() {
        _uiState.update { it.copy(isLoading = true, hasError = false) }
        fetchListings()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        fetchListings()
    }

    fun onCategorySelected(category: ListingCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    private fun fetchListings() {
        viewModelScope.launch {
            runCatching {
                listingRepository.getListings()
            }.onSuccess { listings ->
                _uiState.update {
                    it.copy(
                        listings = listings,
                        isLoading = false,
                        isRefreshing = false,
                        hasError = false,
                    )
                }
            }.onFailure {
                val hasListings = _uiState.value.listings.isNotEmpty()
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
                HomeViewModel(appContainer.listingRepository)
            }
        }
    }
}
