package setty.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.domain.Payment;
import setty.payment.infrastructure.TossConfirmResult;
import setty.payment.presentation.dto.PaymentConfirmRequest;
import setty.payment.repository.PaymentRepository;
import setty.platform.member.domain.Member;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.domain.Order;
import setty.platform.order.service.OrderService;

/**
 * 토스 승인이 확정된 뒤 주문 생성과 결제 저장을 하나의 트랜잭션으로 묶는다.
 * 외부 결제 호출(토스)은 이 트랜잭션 밖에서 이미 끝난 상태로 들어온다.
 * 여기서 {@link OrderService#create}를 호출하므로 매물 선점·주문 저장·OrderRequested 발행이
 * 결제 확정 이후에만 일어난다.
 */
@Component
@RequiredArgsConstructor
public class PaymentRegistrar {

    private final OrderService orderService;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment register(
            final PaymentConfirmRequest request,
            final Member buyer,
            final TossConfirmResult result
    ) {
        final Order order = orderService.create(new OrderCreateRequest(request.listingId()), buyer);
        final Payment payment = new Payment(
                order.getId(),
                request.paymentKey(),
                request.tossOrderId(),
                request.amount(),
                result.approvedAtAsLocalDateTime()
        );

        try {
            return paymentRepository.saveAndFlush(payment);
        } catch (final DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_PAID);
        }
    }
}
