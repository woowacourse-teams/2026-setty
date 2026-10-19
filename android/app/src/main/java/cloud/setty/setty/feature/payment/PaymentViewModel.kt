package cloud.setty.setty.feature.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cloud.setty.setty.SettyApp
import cloud.setty.setty.data.PaymentRepository
import cloud.setty.setty.network.ApiException
import com.tosspayments.paymentsdk.model.TossPaymentResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

enum class PaymentStep {
    // 결제를 시작할 수 있는 상태
    IDLE,

    // PENDING 주문 생성 중
    CREATING_ORDER,

    // 토스 결제창이 떠 있는 상태
    REQUESTING,

    // 서버 승인 대기 중
    CONFIRMING,
}

data class PaymentUiState(
    // 결제할 금액. 주문을 만들기 전에는 매물 상세의 총액, 만든 뒤에는 주문 생성 응답의 총액이다
    val amount: Int = 0,
    val isWidgetLoaded: Boolean = false,
    // 약관 위젯이 아직 동의 상태를 알려주지 않았으면 null
    val isAgreed: Boolean? = null,
    val step: PaymentStep = PaymentStep.IDLE,
) {
    val canPay: Boolean
        get() = isWidgetLoaded && isAgreed != false && step == PaymentStep.IDLE

    // 승인 중에 화면을 벗어나면 결과를 사용자에게 알릴 수 없으므로 뒤로 가기를 막는다
    val canLeave: Boolean
        get() = step != PaymentStep.CONFIRMING
}

sealed interface PaymentEvent {
    data class RequestPayment(val tossOrderId: String, val amount: Int) : PaymentEvent

    data object Completed : PaymentEvent

    data class Failed(val message: String) : PaymentEvent

    data class Notice(val message: String) : PaymentEvent
}

class PaymentViewModel(
    private val listingId: Long,
    amount: Int,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState(amount = amount))
    val uiState = _uiState.asStateFlow()

    private val _event = Channel<PaymentEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    // 결제 요청이 실패했을 때 정리할 주문
    private var requestedTossOrderId: String? = null

    // 서버 승인 요청에 보낼 금액. 주문 생성 응답의 총액이다
    private var requestedAmount: Int? = null

    fun onWidgetLoaded() {
        _uiState.update { it.copy(isWidgetLoaded = true) }
    }

    fun onWidgetLoadFailed(message: String) {
        viewModelScope.launch { _event.send(PaymentEvent.Failed(message)) }
    }

    fun onAgreementChanged(isAgreed: Boolean) {
        _uiState.update { it.copy(isAgreed = isAgreed) }
    }

    fun startPayment() {
        if (!_uiState.value.canPay) return
        updateStep(PaymentStep.CREATING_ORDER)
        viewModelScope.launch {
            runCatching {
                paymentRepository.createOrder(listingId)
            }.onSuccess { order ->
                val tossOrderId = tossOrderIdOf(order.id)
                requestedTossOrderId = tossOrderId
                requestedAmount = order.totalPrice
                _uiState.update { it.copy(amount = order.totalPrice, step = PaymentStep.REQUESTING) }
                _event.send(PaymentEvent.RequestPayment(tossOrderId, order.totalPrice))
            }.onFailure { throwable ->
                updateStep(PaymentStep.IDLE)
                // 결제가 끝나지 않은 PENDING 주문이 매물을 선점 중이면 만료 후 다시 구매할 수 있음을 안내한다
                if (throwable is ApiException && throwable.code == ALREADY_ORDERED) {
                    _event.send(PaymentEvent.Notice(ALREADY_ORDERED_NOTICE))
                } else {
                    _event.send(PaymentEvent.Failed(throwable.toOrderErrorMessage()))
                }
            }
        }
    }

    /** 토스 결제창의 결과를 처리한다. */
    fun onPaymentResult(result: TossPaymentResult) {
        when (result) {
            is TossPaymentResult.Success -> confirm(
                paymentKey = result.paymentKey,
                tossOrderId = result.orderId,
                amount = requestedAmount ?: result.amount.toInt(),
            )

            is TossPaymentResult.Fail -> fail(
                tossOrderId = result.orderId ?: requestedTossOrderId,
                errorCode = result.errorCode,
                errorMessage = result.errorMessage,
            )
        }
        clearRequestedPayment()
    }

    fun onRequestPaymentFailed(message: String) {
        fail(tossOrderId = requestedTossOrderId, errorCode = REQUEST_FAILED, errorMessage = message)
        clearRequestedPayment()
    }

    private fun clearRequestedPayment() {
        requestedTossOrderId = null
        requestedAmount = null
    }

    private fun confirm(paymentKey: String, tossOrderId: String, amount: Int) {
        updateStep(PaymentStep.CONFIRMING)
        viewModelScope.launch {
            runCatching {
                confirmWithRetry(paymentKey, tossOrderId, amount)
            }.onSuccess {
                updateStep(PaymentStep.IDLE)
                _event.send(PaymentEvent.Completed)
            }.onFailure { throwable ->
                // 서버가 승인을 거절한 경우에만 주문을 정리한다.
                // 응답을 받지 못한 경우는 서버에서 승인이 진행됐을 수 있어 실패 처리하면 결제와 주문이 어긋난다.
                if (throwable is ApiException) {
                    notifyFailure(tossOrderId)
                }
                updateStep(PaymentStep.IDLE)
                _event.send(PaymentEvent.Failed(throwable.toConfirmErrorMessage()))
            }
        }
    }

    private fun fail(tossOrderId: String?, errorCode: String, errorMessage: String) {
        tossOrderId?.let(::notifyFailure)
        updateStep(PaymentStep.IDLE)
        // 사용자가 결제창을 직접 닫은 경우는 에러로 보여주지 않는다
        if (errorCode != PAY_PROCESS_CANCELED) {
            viewModelScope.launch { _event.send(PaymentEvent.Failed(errorMessage)) }
        }
    }

    private fun updateStep(step: PaymentStep) {
        _uiState.update { it.copy(step = step) }
    }

    private suspend fun confirmWithRetry(paymentKey: String, tossOrderId: String, amount: Int) {
        repeat(CONFIRM_MAX_ATTEMPTS - 1) {
            try {
                return paymentRepository.confirm(paymentKey, tossOrderId, amount)
            } catch (_: IOException) {
                delay(CONFIRM_RETRY_DELAY_MILLIS)
            }
        }
        paymentRepository.confirm(paymentKey, tossOrderId, amount)
    }

    // 실패 정리는 최선 노력이다. 실패해도 서버가 PENDING 주문을 만료 시간에 정리한다.
    private fun notifyFailure(tossOrderId: String) {
        viewModelScope.launch {
            runCatching { paymentRepository.fail(tossOrderId) }
        }
    }

    private fun Throwable.toOrderErrorMessage(): String = when (this) {
        is ApiException -> message
        is IOException -> "네트워크 에러가 발생했습니다"
        else -> "주문을 만들지 못했습니다"
    }

    private fun Throwable.toConfirmErrorMessage(): String = when (this) {
        is ApiException -> message
        is IOException -> "결제 확인 중 네트워크 에러가 발생했습니다. 주문 내역을 확인해 주세요"
        else -> "결제를 확인하지 못했습니다"
    }

    companion object {
        private const val PAY_PROCESS_CANCELED = "PAY_PROCESS_CANCELED"
        private const val REQUEST_FAILED = "REQUEST_FAILED"
        private const val ALREADY_ORDERED = "ALREADY_ORDERED"

        // 서버는 PENDING 주문을 6분 뒤 만료시키고 1분 주기로 정리한다
        private const val ALREADY_ORDERED_NOTICE = "결제가 진행 중인 매물입니다. 결제가 완료되지 않으면 7분 이내에 다시 구매할 수 있습니다"
        private const val CONFIRM_MAX_ATTEMPTS = 3
        private const val CONFIRM_RETRY_DELAY_MILLIS = 1_000L

        /** 토스 orderId는 매 시도마다 유일해야 하므로 `<주문id>_<랜덤>`으로 만든다. 서버는 앞부분에서 주문 id를 읽는다. */
        fun tossOrderIdOf(orderId: Long): String = "${orderId}_${UUID.randomUUID()}"

        fun factory(listingId: Long, amount: Int) = viewModelFactory {
            initializer {
                val appContainer = (this[APPLICATION_KEY] as SettyApp).appContainer
                PaymentViewModel(listingId, amount, appContainer.paymentRepository)
            }
        }
    }
}
