package setty.payment.presentation.dto;

import setty.payment.domain.Payment;

public record PaymentConfirmResponse(
        Long paymentId,
        Long orderId,
        int amount,
        String status
) {

    public static PaymentConfirmResponse from(final Payment payment) {
        return new PaymentConfirmResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus().name()
        );
    }
}
