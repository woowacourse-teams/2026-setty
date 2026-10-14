package setty.payment.infrastructure;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
    private static final String PAYMENT_PATH = "/v1/payments/{paymentKey}";

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
                    // 같은 키로 다시 승인하면 토스가 처음 응답을 그대로 돌려준다(키 300자 이하·15일 유효, paymentKey는 200자 이하).
                    .header("Idempotency-Key", paymentKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TossConfirmResult.class);
        } catch (final RestClientException exception) {
            log.warn("토스 결제 승인에 실패했습니다. tossOrderId={}", tossOrderId, exception);
            if (mayHaveBeenApproved(exception)) {
                return findApproved(paymentKey, tossOrderId, amount);
            }
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
    }

    // 응답을 받지 못했거나(통신 실패·5xx) 이미 처리됐다는 응답이면 토스에서는 승인됐을 수 있다.
    private boolean mayHaveBeenApproved(final RestClientException exception) {
        if (exception instanceof ResourceAccessException || exception instanceof HttpServerErrorException) {
            return true;
        }
        return exception instanceof HttpClientErrorException clientError
                && clientError.getResponseBodyAsString().contains("\"ALREADY_PROCESSED_PAYMENT\"");
    }

    private TossConfirmResult findApproved(final String paymentKey, final String tossOrderId, final int amount) {
        final TossConfirmResult payment;
        try {
            payment = tossRestClient.get()
                    .uri(PAYMENT_PATH, paymentKey)
                    .retrieve()
                    .body(TossConfirmResult.class);
        } catch (final RestClientException exception) {
            // 예외 메시지에 조회 URL(paymentKey 포함)이 들어가므로 예외 종류만 남긴다.
            log.warn("토스 결제 조회에 실패했습니다. tossOrderId={}, error={}", tossOrderId, exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
        if (payment == null || !payment.isDoneFor(tossOrderId, amount)) {
            log.warn("토스 결제가 기대한 승인 상태가 아닙니다. tossOrderId={}, status={}, totalAmount={}", tossOrderId,
                    payment == null ? null : payment.status(), payment == null ? null : payment.totalAmount());
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
        return payment;
    }
}
