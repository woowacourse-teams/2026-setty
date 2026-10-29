package com.setty.android.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.setty.android.SettyApp
import com.setty.android.data.OrderRepository
import com.setty.android.feature.common.LoadUiState
import com.setty.android.feature.common.isUnauthorized
import com.setty.android.feature.common.toLoadError
import com.setty.android.feature.common.toUserMessage
import com.setty.android.model.Order
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersUiState(
    val orders: LoadUiState<List<Order>> = LoadUiState.Loading,
    val isRefreshing: Boolean = false,
)

sealed interface OrdersEvent {
    data class Message(val message: String) : OrdersEvent

    data object RequireLogin : OrdersEvent
}

class OrdersViewModel(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<OrdersEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    /**
     * 화면에 들어올 때마다 다시 불러온다. 이미 보여주는 목록이 있으면 로딩을 띄우지 않는다.
     * ViewModel이 Activity 범위라 새로 연 화면이면 [reset]으로 이전 화면(다른 계정일 수 있다)의 목록을 버린다.
     */
    fun load(reset: Boolean = false) {
        if (reset || _uiState.value.orders !is LoadUiState.Success) {
            _uiState.value = OrdersUiState()
        }
        fetch()
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        fetch()
    }

    private fun fetch() {
        viewModelScope.launch {
            runCatching {
                orderRepository.getMyOrders()
            }.onSuccess { orders ->
                _uiState.update { it.copy(orders = LoadUiState.Success(orders), isRefreshing = false) }
            }.onFailure { throwable ->
                val hasOrders = _uiState.value.orders is LoadUiState.Success
                _uiState.update {
                    it.copy(
                        orders = if (hasOrders) it.orders else throwable.toLoadError(),
                        isRefreshing = false,
                    )
                }
                if (hasOrders) {
                    _event.send(
                        if (throwable.isUnauthorized) {
                            OrdersEvent.RequireLogin
                        } else {
                            OrdersEvent.Message(throwable.toUserMessage())
                        }
                    )
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                OrdersViewModel(appContainer.orderRepository)
            }
        }
    }
}
