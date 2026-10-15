package setty.platform.order.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.platform.PaymentOrderReader;
import setty.platform.member.domain.Member;
import setty.platform.order.controller.dto.MyOrderResponse;
import setty.platform.order.controller.dto.OrderCancellationResponse;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.controller.dto.OrderCreateResponse;
import setty.platform.order.domain.Order;
import setty.platform.order.service.OrderCompletionService;
import setty.platform.order.service.OrderService;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final OrderCompletionService orderCompletionService;
    private final PaymentOrderReader paymentOrderReader;

    public OrderController(
            final OrderService orderService,
            final OrderCompletionService orderCompletionService,
            final PaymentOrderReader paymentOrderReader
    ) {
        this.orderService = orderService;
        this.orderCompletionService = orderCompletionService;
        this.paymentOrderReader = paymentOrderReader;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<OrderCreateResponse> create(
            @LoginMember final Member member,
            @Valid @RequestBody final OrderCreateRequest request
    ) {
        final Order order = orderService.pending(request, member);
        // 토스 승인 때 검증하는 금액과 같은 계산으로 결제할 총액을 준다. 클라이언트는 이 값으로 결제창을 연다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderCreateResponse.from(order, paymentOrderReader.payableAmount(order.getId())));
    }

    @GetMapping("/api/me/orders")
    public ResponseEntity<List<MyOrderResponse>> findMyOrders(@LoginMember final Member member) {
        return ResponseEntity.ok(orderService.findMyOrders(member.getId()));
    }

    @GetMapping("/api/orders/{id}")
    public ResponseEntity<MyOrderResponse> findMyOrder(
            @LoginMember final Member member,
            @PathVariable final Long id
    ) {
        return ResponseEntity.ok(orderService.findMyOrder(id, member.getId()));
    }

    @PostMapping("/api/orders/{id}/cancellations")
    public ResponseEntity<OrderCancellationResponse> requestCancellation(
            @LoginMember final Member member,
            @PathVariable final Long id
    ) {
        return ResponseEntity.accepted()
                .body(orderService.requestCancellation(id, member.getId()));
    }

    @PostMapping("/api/orders/{id}/completion")
    public ResponseEntity<Void> confirmCompletion(
            @LoginMember final Member member,
            @PathVariable final Long id
    ) {
        orderCompletionService.confirmByBuyer(id, member.getId());
        return ResponseEntity.noContent().build();
    }
}
