package com.aksworns22.setty.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aksworns22.setty.SettyApp
import com.aksworns22.setty.data.OrderRepository
import com.aksworns22.setty.feature.common.LoadUiState
import com.aksworns22.setty.feature.common.isUnauthorized
import com.aksworns22.setty.feature.common.toLoadError
import com.aksworns22.setty.feature.common.toUserMessage
import com.aksworns22.setty.model.Order
import com.aksworns22.setty.model.OrderStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderDetailUiState(
    val order: LoadUiState<Order> = LoadUiState.Loading,
    val isRefreshing: Boolean = false,
    val isCancelling: Boolean = false,
)

sealed interface OrderDetailEvent {
    data class Message(val message: String) : OrderDetailEvent

    data object RequireLogin : OrderDetailEvent
}

class OrderDetailViewModel(
    private val orderId: Long,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<OrderDetailEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    /**
     * 화면에 들어올 때마다 다시 불러온다. 이미 보여주는 주문이 있으면 로딩을 띄우지 않는다.
     * 새로 연 화면이면 [reset]으로 이전 상태를 버린다.
     */
    fun load(reset: Boolean = false) {
        if (reset || _uiState.value.order !is LoadUiState.Success) {
            _uiState.value = OrderDetailUiState()
        }
        viewModelScope.launch { fetch() }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch { fetch() }
    }

    /**
     * 취소를 요청한다. 202 응답은 접수 결과일 뿐이라 주문 상세를 다시 조회해 최종 상태를 확인한다.
     * 배송이 취소를 처리하는 동안은 CANCEL_PENDING이므로 몇 번 더 조회한다.
     */
    fun cancel() {
        if (_uiState.value.isCancelling) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true) }
            runCatching {
                orderRepository.cancel(orderId)
            }.onSuccess {
                _event.send(OrderDetailEvent.Message("취소 요청을 접수했어요"))
                repeat(CANCEL_POLL_COUNT) {
                    val order = fetch()
                    if (order?.orderStatus != OrderStatus.CANCEL_PENDING) return@onSuccess
                    delay(CANCEL_POLL_DELAY_MILLIS)
                }
            }.onFailure { throwable ->
                _event.send(throwable.toEvent())
            }
            _uiState.update { it.copy(isCancelling = false) }
        }
    }

    private suspend fun fetch(): Order? {
        return runCatching {
            orderRepository.getOrder(orderId)
        }.onSuccess { order ->
            _uiState.update { it.copy(order = LoadUiState.Success(order), isRefreshing = false) }
        }.onFailure { throwable ->
            val hasOrder = _uiState.value.order is LoadUiState.Success
            _uiState.update {
                it.copy(
                    order = if (hasOrder) it.order else throwable.toLoadError(),
                    isRefreshing = false,
                )
            }
            if (hasOrder) _event.send(throwable.toEvent())
        }.getOrNull()
    }

    private fun Throwable.toEvent(): OrderDetailEvent =
        if (isUnauthorized) OrderDetailEvent.RequireLogin else OrderDetailEvent.Message(toUserMessage())

    companion object {
        private const val CANCEL_POLL_COUNT = 3
        private const val CANCEL_POLL_DELAY_MILLIS = 1_500L

        fun factory(orderId: Long) = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                OrderDetailViewModel(orderId, appContainer.orderRepository)
            }
        }
    }
}
