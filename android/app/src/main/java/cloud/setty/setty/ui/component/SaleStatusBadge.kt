package cloud.setty.setty.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cloud.setty.setty.core.model.SaleStatus
import cloud.setty.setty.model.label

@Composable
fun SaleStatusBadge(
    saleStatus: SaleStatus,
    modifier: Modifier = Modifier,
    isPaymentInProgress: Boolean = false,
) {
    val (containerColor, contentColor) = when {
        isPaymentInProgress -> MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer

        saleStatus == SaleStatus.AVAILABLE -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer

        saleStatus == SaleStatus.RESERVED -> MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer

        else -> MaterialTheme.colorScheme.surfaceContainerHighest to
                MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Text(
            text = if (isPaymentInProgress) PAYMENT_IN_PROGRESS_LABEL else saleStatus.label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

const val PAYMENT_IN_PROGRESS_LABEL = "결제 진행 중"
