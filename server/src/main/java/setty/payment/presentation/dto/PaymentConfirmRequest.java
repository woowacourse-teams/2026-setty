package setty.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentConfirmRequest(
        @NotNull Long listingId,
        @NotBlank String tossOrderId,
        @NotBlank String paymentKey,
        @NotNull @Positive Integer amount
) {
}
