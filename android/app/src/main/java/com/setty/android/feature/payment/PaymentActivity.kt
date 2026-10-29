package com.setty.android.feature.payment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.setty.android.BuildConfig
import com.setty.android.ui.theme.SettyTheme
import com.tosspayments.paymentsdk.PaymentWidget
import com.tosspayments.paymentsdk.model.AgreementStatus
import com.tosspayments.paymentsdk.model.AgreementStatusListener
import com.tosspayments.paymentsdk.model.PaymentCallback
import com.tosspayments.paymentsdk.model.PaymentWidgetStatusListener
import com.tosspayments.paymentsdk.model.TossPaymentResult
import com.tosspayments.paymentsdk.view.Agreement
import com.tosspayments.paymentsdk.view.PaymentMethod

/**
 * 토스 결제위젯으로 매물을 결제하는 화면. 결제가 완료되면 RESULT_OK로 닫힌다.
 *
 * 결제위젯은 생성 시점에 ActivityResult를 등록하므로 Activity가 STARTED 되기 전인 onCreate에서 만들어야 하고,
 * AppCompatActivity를 요구한다. 그래서 Compose 내비게이션이 아닌 별도 Activity로 분리한다.
 */
class PaymentActivity : AppCompatActivity() {

    private val listingId by lazy { intent.getLongExtra(EXTRA_LISTING_ID, -1) }
    private val orderName by lazy { intent.getStringExtra(EXTRA_ORDER_NAME).orEmpty() }
    private val amount by lazy { intent.getIntExtra(EXTRA_AMOUNT, 0) }

    private val viewModel: PaymentViewModel by viewModels { PaymentViewModel.factory(listingId, amount) }

    private lateinit var paymentWidget: PaymentWidget

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        paymentWidget = PaymentWidget(
            activity = this,
            clientKey = BuildConfig.TOSS_CLIENT_KEY,
            customerKey = ANONYMOUS_CUSTOMER_KEY,
        )

        setContent {
            SettyTheme {
                PaymentScreen(
                    viewModel = viewModel,
                    onPaymentMethodCreated = ::renderPaymentMethods,
                    onAgreementCreated = ::renderAgreement,
                    onRequestPayment = ::requestPayment,
                    onBack = ::finish,
                    onComplete = {
                        setResult(RESULT_OK)
                        finish()
                    },
                )
            }
        }
    }

    private fun renderPaymentMethods(view: PaymentMethod) {
        paymentWidget.renderPaymentMethods(
            method = view,
            amount = PaymentMethod.Rendering.Amount(amount),
            paymentWidgetStatusListener = object : PaymentWidgetStatusListener {
                override fun onLoad() = viewModel.onWidgetLoaded()

                override fun onFail(fail: TossPaymentResult.Fail) =
                    viewModel.onWidgetLoadFailed(fail.errorMessage)
            },
        )
    }

    private fun renderAgreement(view: Agreement) {
        paymentWidget.renderAgreement(
            agreement = view,
            paymentWidgetStatusListener = object : PaymentWidgetStatusListener {
                override fun onLoad() {
                    paymentWidget.addAgreementStatusListener(object : AgreementStatusListener {
                        override fun onAgreementStatusChanged(agreementStatus: AgreementStatus) =
                            viewModel.onAgreementChanged(agreementStatus.agreedRequiredTerms)
                    })
                }

                override fun onFail(fail: TossPaymentResult.Fail) =
                    viewModel.onWidgetLoadFailed(fail.errorMessage)
            },
        )
    }

    private fun requestPayment(tossOrderId: String, amount: Int) {
        try {
            // 위젯은 매물 상세의 총액으로 렌더링됐으므로 서버가 검증할 주문 총액으로 맞춘 뒤 결제창을 연다
            paymentWidget.updateAmount(amount)
            paymentWidget.requestPayment(
                paymentInfo = PaymentMethod.PaymentInfo(orderId = tossOrderId, orderName = orderName),
                paymentCallback = object : PaymentCallback {
                    override fun onPaymentSuccess(success: TossPaymentResult.Success) =
                        viewModel.onPaymentResult(success)

                    override fun onPaymentFailed(fail: TossPaymentResult.Fail) =
                        viewModel.onPaymentResult(fail)
                },
            )
        } catch (e: IllegalAccessException) {
            // 결제위젯이 렌더링되지 않은 상태에서 요청한 경우. 이미 만든 주문은 정리한다.
            viewModel.onRequestPaymentFailed(e.message ?: "결제 수단을 불러오지 못했습니다")
        }
    }

    companion object {
        private const val EXTRA_LISTING_ID = "extraListingId"
        private const val EXTRA_ORDER_NAME = "extraOrderName"
        private const val EXTRA_AMOUNT = "extraAmount"

        // 웹 결제와 같이 비회원 결제로 연동한다(토스 SDK의 ANONYMOUS 값)
        private const val ANONYMOUS_CUSTOMER_KEY = "@@ANONYMOUS"

        fun intent(context: Context, listingId: Long, orderName: String, amount: Int): Intent =
            Intent(context, PaymentActivity::class.java)
                .putExtra(EXTRA_LISTING_ID, listingId)
                .putExtra(EXTRA_ORDER_NAME, orderName)
                .putExtra(EXTRA_AMOUNT, amount)
    }
}
