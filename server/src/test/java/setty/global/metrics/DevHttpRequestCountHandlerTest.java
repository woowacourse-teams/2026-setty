package setty.global.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DevHttpRequestCountHandlerTest {

    @Test
    void excludesHealthChecksUnderContextPathButIncludesSimilarlyNamedBusinessRoute() {
        var registry = new SimpleMeterRegistry();
        try {
            var handler = new DevHttpRequestCountHandler(registry);
            complete(handler, "/app/actuator/health", "/app", 503);
            complete(handler, "/app/actuator/health/readiness", "/app", 503);
            complete(handler, "/app/actuator/healthy-orders", "/app", 500);
            complete(handler, "/app/api/listings", "/app", 404);
            complete(handler, "/app/api/listings", "/app", 200);
            assertThat(registry.get("setty.http.requests.completed").counter().count()).isEqualTo(3);
            assertThat(registry.get("setty.http.requests.server.errors").counter().count()).isEqualTo(1);
        } finally {
            registry.close();
        }
    }

    @Test
    void selectsServletContextBeforeNameIsAssignedAndIgnoresOtherObservations() {
        var registry = new SimpleMeterRegistry();
        try {
            var handler = new DevHttpRequestCountHandler(registry);
            var context = new ServerRequestObservationContext(
                    new MockHttpServletRequest("GET", "/api/listings"), new MockHttpServletResponse());
            assertThat(handler.supportsContext(context)).isTrue();
            assertThat(handler.supportsContext(new Observation.Context())).isFalse();
            context.setName("other.observation");
            handler.onStop(context);
            assertThat(registry.get("setty.http.requests.completed").counter().count()).isZero();
        } finally {
            registry.close();
        }
    }

    private void complete(DevHttpRequestCountHandler handler, String uri, String contextPath, int status) {
        var request = new MockHttpServletRequest("GET", uri);
        request.setContextPath(contextPath);
        var response = new MockHttpServletResponse();
        response.setStatus(status);
        var context = new ServerRequestObservationContext(request, response);
        context.setName("http.server.requests");
        handler.onStop(context);
    }
}
