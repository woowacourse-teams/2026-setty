package setty.payment.infrastructure;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

/**
 * 토스페이먼츠 결제 승인 API 호출을 담당한다.
 * 외부 HTTP 호출이므로 DB 트랜잭션 밖에서 호출한다 (PaymentService 참고).
 */
@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentClient.class);
    private static final String CONFIRM_PATH = "/v1/payments/confirm";

    private final RestClient tossRestClient;

    public TossConfirmResult confirm(final String paymentKey, final String tossOrderId, final int amount) {
        final Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", tossOrderId,
                "amount", amount
        );

        try {
            return tossRestClient.post()
                    .uri(CONFIRM_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TossConfirmResult.class);
        } catch (final RestClientException exception) {
            // 4xx/5xx 응답 및 통신 오류 모두 승인 실패로 취급한다.
            log.warn("토스 결제 승인에 실패했습니다. tossOrderId={}", tossOrderId, exception);
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
    }
}
