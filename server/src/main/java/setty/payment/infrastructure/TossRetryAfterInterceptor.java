package setty.payment.infrastructure;

import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 토스가 요청 한도 초과(429)로 답하면 Retry-After만큼 기다렸다가 한 번 더 보낸다.
 * 429는 토스가 요청을 처리하지 않았다는 응답이고, 승인·취소는 멱등키를 실어 보내므로 다시 보내도 한 번만 처리된다.
 * 승인은 구매자가 응답을 기다리는 요청이라 짧게만 기다린다. 더 기다리라고 하면 기다리지 않고 429를 그대로 돌려준다.
 */
public class TossRetryAfterInterceptor implements ClientHttpRequestInterceptor {

    static final int MAX_RETRIES = 1;
    static final Duration MAX_WAIT = Duration.ofSeconds(2);
    // Retry-After가 없거나 초 단위가 아니면 1초 뒤에 다시 보낸다.
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(1);

    @Override
    public ClientHttpResponse intercept(
            final HttpRequest request,
            final byte[] body,
            final ClientHttpRequestExecution execution
    ) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        for (int retry = 0; retry < MAX_RETRIES && isTooManyRequests(response); retry++) {
            final Duration wait = retryAfter(response);
            if (wait.compareTo(MAX_WAIT) > 0) {
                return response;
            }
            response.close();
            sleep(wait);
            response = execution.execute(request, body);
        }
        return response;
    }

    private static boolean isTooManyRequests(final ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
    }

    private static Duration retryAfter(final ClientHttpResponse response) {
        final String value = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return DEFAULT_WAIT;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (final NumberFormatException e) {
            return DEFAULT_WAIT;
        }
    }

    private static void sleep(final Duration wait) throws IOException {
        try {
            Thread.sleep(wait);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("토스 재요청 대기 중 인터럽트되었습니다.", e);
        }
    }
}
