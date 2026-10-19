package cloud.setty.setty.feature.payment

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.setty.setty.R
import cloud.setty.setty.model.formatWon
import com.tosspayments.paymentsdk.view.Agreement
import com.tosspayments.paymentsdk.view.PaymentMethod

@Composable
fun PaymentScreen(
    viewModel: PaymentViewModel,
    onPaymentMethodCreated: (PaymentMethod) -> Unit,
    onAgreementCreated: (Agreement) -> Unit,
    onRequestPayment: (tossOrderId: String, amount: Int) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val currentOnRequestPayment by rememberUpdatedState(onRequestPayment)
    val currentOnComplete by rememberUpdatedState(onComplete)
    BackHandler(enabled = !uiState.canLeave) {}
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is PaymentEvent.RequestPayment -> currentOnRequestPayment(event.tossOrderId, event.amount)
                PaymentEvent.Completed -> currentOnComplete()
                is PaymentEvent.Failed -> snackbarHostState.showSnackbar(event.message)
                is PaymentEvent.Notice ->
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    PaymentContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onPaymentMethodCreated = onPaymentMethodCreated,
        onAgreementCreated = onAgreementCreated,
        onBack = onBack,
        onPayClick = viewModel::startPayment,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PaymentContent(
    uiState: PaymentUiState,
    snackbarHostState: SnackbarHostState,
    onPaymentMethodCreated: (PaymentMethod) -> Unit,
    onAgreementCreated: (Agreement) -> Unit,
    onBack: () -> Unit,
    onPayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("결제") },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        enabled = uiState.canLeave,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_arrow_back_24),
                            contentDescription = "뒤로 가기",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onPayClick,
                enabled = uiState.canPay,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(
                    text = when (uiState.step) {
                        PaymentStep.IDLE -> "${formatWon(uiState.amount)} 결제하기"
                        PaymentStep.CONFIRMING -> "결제 확인 중"
                        else -> "결제 진행 중"
                    },
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                )
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        // 결제 수단과 약관 영역은 토스 결제위젯이 그린다
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AndroidView(
                factory = { context -> PaymentMethod(context).also(onPaymentMethodCreated) },
                modifier = Modifier.fillMaxWidth(),
            )
            AndroidView(
                factory = { context -> Agreement(context).also(onAgreementCreated) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
