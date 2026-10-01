package setty.global.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.util.EnumSet;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 매핑된 API의 outcome별 빈 Timer를 등록해 첫 요청 전에도 0건 표본을 발행할 수 있게 한다.
 * record(0)은 가짜 요청을 추가하므로 사용하지 않는다. 첫 발행 이전 요청의 누락은 해결하지 못한다.
 */
final class HttpRequestHistogramInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final List<String> OUTCOMES = List.of(
            "INFORMATIONAL", "SUCCESS", "REDIRECTION", "CLIENT_ERROR", "SERVER_ERROR", "UNKNOWN");

    private final OtlpMeterRegistry registry;
    private final ObjectProvider<RequestMappingHandlerMapping> mappings;

    HttpRequestHistogramInitializer(OtlpMeterRegistry registry,
            ObjectProvider<RequestMappingHandlerMapping> mappings) {
        this.registry = registry;
        this.mappings = mappings;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        mappings.orderedStream().forEach(mapping -> mapping.getHandlerMethods().keySet().forEach(this::register));
    }

    private void register(RequestMappingInfo mapping) {
        if (mapping.getProducesCondition().getProducibleMediaTypes().stream()
                .anyMatch(MediaType.TEXT_EVENT_STREAM::equalsTypeAndSubtype)) {
            return;
        }
        var declaredMethods = mapping.getMethodsCondition().getMethods();
        var methods = declaredMethods.isEmpty()
                ? EnumSet.allOf(RequestMethod.class) : EnumSet.copyOf(declaredMethods);
        if (methods.contains(RequestMethod.GET)) {
            methods.add(RequestMethod.HEAD);
        }
        for (String uri : mapping.getPatternValues()) {
            // 헬스 체크와 오류 재디스패치 경로는 일반 API 응답 시간의 사전 등록에서 제외한다.
            if (uri.equals("/error") || uri.equals("/actuator/health") || uri.startsWith("/actuator/health/")) {
                continue;
            }
            for (RequestMethod method : methods) {
                for (String outcome : OUTCOMES) {
                    Timer.builder("http.server.requests")
                            .tags("uri", uri, "method", method.name(), "outcome", outcome)
                            .register(registry);
                }
            }
        }
    }
}
