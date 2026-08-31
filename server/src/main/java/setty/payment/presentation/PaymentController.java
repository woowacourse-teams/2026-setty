package setty.payment.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import setty.global.auth.LoginMember;
import setty.payment.application.PaymentService;
import setty.payment.presentation.dto.PaymentConfirmRequest;
import setty.payment.presentation.dto.PaymentConfirmResponse;
import setty.platform.member.domain.Member;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/payments/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirm(
            @LoginMember final Member member,
            @Valid @RequestBody final PaymentConfirmRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PaymentConfirmResponse.from(paymentService.confirm(request, member)));
    }
}
