package setty.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentFailRequest(
        @NotBlank String orderId
) {
}
