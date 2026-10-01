package setty.payment.presentation.dto;

import setty.payment.domain.Payment;

public record PaymentConfirmResponse(
        Long orderId,
        String status,
        int amount
) {

    public static PaymentConfirmResponse from(final Payment payment) {
        return new PaymentConfirmResponse(payment.getOrderId(), payment.getStatus().name(), payment.getAmount());
    }
}
