package setty.platform.order.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.platform.member.domain.Member;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.controller.dto.OrderCreateResponse;
import setty.platform.order.service.OrderService;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(final OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders")
    public ResponseEntity<OrderCreateResponse> create(
            @LoginMember final Member member,
            @Valid @RequestBody final OrderCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderCreateResponse.from(orderService.create(request, member)));
    }
}
