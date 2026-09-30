package com.aksworns22.setty.feature.sellerpage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.ListingRepository
import com.aksworns22.setty.data.UserRepository
import com.aksworns22.setty.model.MyListing
import com.aksworns22.setty.model.SaleStatus
import com.aksworns22.setty.network.ApiException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class SellerPageUiState(
    val listings: List<MyListing> = emptyList(),
    val isLoading: Boolean = true,
    val error: SellerPageError? = null,
    val isLoggingOut: Boolean = false,
) {
    fun countOf(saleStatus: SaleStatus): Int = listings.count { it.saleStatus == saleStatus }
}

enum class SellerPageError {
    UNAUTHORIZED, NETWORK_ERROR, UNKNOWN_ERROR,
}

enum class SellerPageEvent {
    LOGGED_OUT,
}

class SellerPageViewModel(
    private val listingRepository: ListingRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SellerPageUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<SellerPageEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    /** 화면에 들어올 때마다 호출해 다른 계정의 매물이 남아 보이지 않게 한다. */
    fun load() {
        _uiState.value = SellerPageUiState()
        viewModelScope.launch {
            runCatching {
                listingRepository.getMyListings()
            }.onSuccess { listings ->
                _uiState.update { it.copy(listings = listings, isLoading = false) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, error = throwable.toSellerPageError())
                }
            }
        }
    }

    fun logout() {
        if (_uiState.value.isLoggingOut) return
        _uiState.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            userRepository.logout()
            _event.send(SellerPageEvent.LOGGED_OUT)
        }
    }

    private fun Throwable.toSellerPageError(): SellerPageError = when (this) {
        is ApiException -> when (code) {
            "INVALID_TOKEN" -> SellerPageError.UNAUTHORIZED
            else -> SellerPageError.UNKNOWN_ERROR
        }

        is IOException -> SellerPageError.NETWORK_ERROR
        else -> SellerPageError.UNKNOWN_ERROR
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                SellerPageViewModel(appContainer.listingRepository, appContainer.userRepository)
            }
        }
    }
}
