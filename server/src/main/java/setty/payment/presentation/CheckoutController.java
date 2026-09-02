package setty.payment.presentation;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import setty.global.exception.BusinessException;
import setty.payment.application.PaymentService;

/**
 * 토스 결제창이 성공/실패 시 브라우저를 돌려보내는 복귀 엔드포인트.
 *
 * <p>{@code /api/**} 밖 경로라 인증 인터셉터를 타지 않는다(리다이렉트에는 Authorization 헤더가 실리지 않는다).
 * 구매자 신원은 토큰이 아니라 <b>주문(orderId)</b>에서 파생하고, 결제의 진정성은 토스가 검증한 paymentKey로 보장된다.
 * 처리 결과와 무관하게 프론트 결과 페이지로 302 리다이렉트한다.
 */
@RestController
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    private final PaymentService paymentService;
    private final String clientRedirectUrl;

    public CheckoutController(
            final PaymentService paymentService,
            @Value("${setty.payment.client-redirect-url}") final String clientRedirectUrl
    ) {
        this.paymentService = paymentService;
        this.clientRedirectUrl = clientRedirectUrl;
    }

    @GetMapping("/payments/success")
    public ResponseEntity<Void> success(
            @RequestParam final String paymentKey,
            @RequestParam final String orderId,
            @RequestParam final int amount
    ) {
        try {
            paymentService.confirm(orderId, paymentKey, amount);
            return redirect("success", orderId, null);
        } catch (final BusinessException e) {
            log.warn("결제 승인 처리에 실패했습니다. orderId={}, code={}", orderId, e.getErrorCode(), e);
            failQuietly(orderId);
            return redirect("fail", orderId, e.getErrorCode().name());
        }
    }

    @GetMapping("/payments/fail")
    public ResponseEntity<Void> fail(
            @RequestParam(required = false) final String code,
            @RequestParam(required = false) final String message,
            @RequestParam final String orderId
    ) {
        log.info("결제 실패 복귀. orderId={}, code={}, message={}", orderId, code, message);
        failQuietly(orderId);
        return redirect("fail", orderId, code);
    }

    // 실패 정리는 최선 노력 — 잘못된 orderId 등으로 실패해도 사용자 리다이렉트는 막지 않는다.
    private void failQuietly(final String orderId) {
        try {
            paymentService.fail(orderId);
        } catch (final BusinessException e) {
            log.warn("결제 실패 정리에 실패했습니다. orderId={}, code={}", orderId, e.getErrorCode(), e);
        }
    }

    private ResponseEntity<Void> redirect(final String result, final String orderId, final String reason) {
        final UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(clientRedirectUrl)
                .queryParam("payment", result)
                .queryParam("orderId", orderId);
        if (reason != null) {
            builder.queryParam("reason", reason);
        }
        final URI location = builder.build(true).toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
