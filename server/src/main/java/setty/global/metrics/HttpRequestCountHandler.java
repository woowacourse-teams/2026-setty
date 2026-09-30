package setty.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * 전체 요청/5xx 카운터를 시작 시 0으로 등록한다.
 * 첫 발행 이전의 요청은 여전히 rate로 복원할 수 없으므로 누적값과 수집 최신성도 함께 확인한다.
 */
final class HttpRequestCountHandler implements ObservationHandler<ServerRequestObservationContext> {

    private final Counter completed;
    private final Counter serverErrors;

    HttpRequestCountHandler(MeterRegistry registry) {
        completed = Counter.builder("setty.http.requests.completed")
                .description("Completed HTTP requests excluding actuator health checks")
                .register(registry);
        serverErrors = Counter.builder("setty.http.requests.server.errors")
                .description("Completed HTTP 5xx responses excluding actuator health checks")
                .register(registry);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        // Convention 기반 Observation은 handler 선택 후에 이름을 설정한다.
        return context instanceof ServerRequestObservationContext;
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        if (!"http.server.requests".equals(context.getName())) {
            return;
        }
        var request = context.getCarrier();
        var response = context.getResponse();
        if (request == null || response == null) {
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")) {
            return;
        }
        completed.increment();
        if (response.getStatus() >= 500 && response.getStatus() < 600) {
            serverErrors.increment();
        }
    }
}
