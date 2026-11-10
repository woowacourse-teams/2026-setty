package setty.payment.infrastructure;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

/**
 * 토스페이먼츠 결제 승인·조회·취소 API 호출을 담당한다.
 * 외부 HTTP 호출이므로 DB 트랜잭션 밖에서 호출한다 (PaymentService 참고).
 * 조회·취소 URL에는 paymentKey가 들어가므로 예외 메시지 대신 예외 종류와 에러 코드만 남긴다.
 */
@Component
@RequiredArgsConstructor
public class TossPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentClient.class);
    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String PAYMENT_PATH = "/v1/payments/{paymentKey}";
    private static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String NOT_FOUND_PAYMENT = "NOT_FOUND_PAYMENT";
    // 토스 결제 취소 에러표에서 "잠시 후 다시 시도해주세요"로 안내하는 4xx. 5xx와 409는 상태 코드로 가린다.
    private static final Set<String> RETRY_LATER_CANCEL_CODES = Set.of("PROVIDER_ERROR", "FORBIDDEN_CONSECUTIVE_REQUEST");
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");

    private final RestClient tossRestClient;

    /**
     * 승인이 확인되면 결과를 돌려준다. 토스가 명확히 거절하면 {@code PAYMENT_CONFIRM_FAILED}를,
     * 승인됐을 수 있는데 확인하지 못하면 {@link TossPaymentUncertainException}을 던진다.
     */
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
                    .header(IDEMPOTENCY_KEY, paymentKey)
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

    public TossPaymentLookup find(final String paymentKey) {
        try {
            final TossConfirmResult payment = tossRestClient.get()
                    .uri(PAYMENT_PATH, paymentKey)
                    .retrieve()
                    .body(TossConfirmResult.class);
            return payment == null ? new TossPaymentLookup.Unavailable() : new TossPaymentLookup.Found(payment);
        } catch (final RestClientException exception) {
            final String code = errorCode(exception);
            // 404 중 NOT_FOUND_PAYMENT만 "토스에 없는 결제"다. 경로 오류(NOT_FOUND)나 본문 없는 404는 설정 문제일 수 있어 다시 묻는다.
            if (exception instanceof HttpClientErrorException.NotFound && NOT_FOUND_PAYMENT.equals(code)) {
                return new TossPaymentLookup.NotFound();
            }
            log.warn("토스 결제 조회에 실패했습니다. error={}, code={}", exception.getClass().getSimpleName(), code);
            return new TossPaymentLookup.Unavailable();
        }
    }

    /** 전액 취소한다. 같은 환불은 늘 같은 멱등키로 보내므로 다시 보내도 한 번만 취소된다. */
    public TossCancelResult cancel(final String paymentKey, final String cancelReason, final String idempotencyKey) {
        try {
            final TossCancelResult.Canceled canceled = tossRestClient.post()
                    .uri(CANCEL_PATH, paymentKey)
                    .header(IDEMPOTENCY_KEY, idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancelReason", cancelReason))
                    .retrieve()
                    .body(TossCancelResult.Canceled.class);
            return canceled == null ? new TossCancelResult.Retryable("EMPTY_RESPONSE") : canceled;
        } catch (final ResourceAccessException exception) {
            return new TossCancelResult.Retryable("NO_RESPONSE");
        } catch (final HttpStatusCodeException exception) {
            return classifyCancelFailure(exception);
        } catch (final RestClientException exception) {
            log.warn("토스 결제 취소 응답을 읽지 못했습니다. error={}", exception.getClass().getSimpleName());
            return new TossCancelResult.Retryable("INVALID_RESPONSE");
        }
    }

    private TossCancelResult classifyCancelFailure(final HttpStatusCodeException exception) {
        final String code = errorCode(exception);
        // 429는 한 번 다시 보낸 뒤에도 한도를 넘은 경우다(TossRetryAfterInterceptor). 토스가 처리하지 않았으므로 다음 배치에서 보낸다.
        if (exception.getStatusCode().is5xxServerError()
                || exception.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)
                || exception.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)
                || RETRY_LATER_CANCEL_CODES.contains(code)) {
            return new TossCancelResult.Retryable(code);
        }
        if ("ALREADY_CANCELED_PAYMENT".equals(code)) {
            return new TossCancelResult.AlreadyCanceled();
        }
        return new TossCancelResult.Rejected(code);
    }

    // 토스가 4xx로 거절한 것만 명확한 실패다. 응답을 못 받았거나(통신 실패·5xx·읽을 수 없는 응답),
    // 같은 멱등키 요청이 처리 중이거나(409), 이미 처리됐다는 응답이면 토스에서는 승인됐을 수 있다.
    // 한 번 다시 보낸 뒤에도 남은 429는 토스가 처리하지 않은 요청이므로 명확한 실패로 본다.
    private boolean mayHaveBeenApproved(final RestClientException exception) {
        if (!(exception instanceof HttpClientErrorException clientError)) {
            return true;
        }
        return clientError.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)
                || "ALREADY_PROCESSED_PAYMENT".equals(errorCode(clientError));
    }

    private TossConfirmResult findApproved(final String paymentKey, final String tossOrderId, final int amount) {
        final TossPaymentLookup lookup = find(paymentKey);
        if (lookup instanceof TossPaymentLookup.Found(final TossConfirmResult payment)) {
            if (payment.isDoneFor(tossOrderId, amount)) {
                return payment;
            }
            log.warn("토스 결제가 기대한 승인 상태가 아닙니다. tossOrderId={}, status={}, totalAmount={}",
                    tossOrderId, payment.status(), payment.totalAmount());
            if (payment.isNotApproved()) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
            }
        }
        if (lookup instanceof TossPaymentLookup.NotFound) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED);
        }
        throw new TossPaymentUncertainException();
    }

    private static String errorCode(final RestClientException exception) {
        if (!(exception instanceof HttpStatusCodeException statusError)) {
            return null;
        }
        final Matcher matcher = ERROR_CODE.matcher(statusError.getResponseBodyAsString());
        return matcher.find() ? matcher.group(1) : "HTTP_" + statusError.getStatusCode().value();
    }
}
