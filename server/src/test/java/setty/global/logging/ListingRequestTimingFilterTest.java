package setty.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ListingRequestTimingFilterTest {

    private final ListingRequestTimingFilter filter = new ListingRequestTimingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ListingRequestTiming.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
        logs.stop();
        MDC.clear();
    }

    @Test
    void recordsOneSummaryWithIdAndHandledErrorStatusWithoutSensitiveData() throws Exception {
        MockHttpServletRequest request = request();
        request.setQueryString("token=secret-query");
        request.addHeader("Authorization", "secret-header");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request, response, (req, res) -> filter.doFilter(req, res, (r, s) -> {
            ListingRequestTiming.measure("listings", () -> {
                ListingRequestTiming.count("listing_count", 5);
                return null;
            });
            response.setStatus(400);
            response.getWriter().write("secret-body");
        }));

        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.getFirst();
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", response.getHeader("X-Request-Id"));
        assertThat(fields(event)).containsEntry("status", 400).containsEntry("response_complete", true)
                .containsEntry("outcome", "completed").containsEntry("listing_count", 5)
                .containsEntry("listings_completed", true);
        assertThat((double) fields(event).get("filter_ms"))
                .isGreaterThanOrEqualTo((double) fields(event).get("listings_ms"));
        assertThat(event.getFormattedMessage() + fields(event)).doesNotContain("secret");
        assertThat(response.getContentAsString()).isEqualTo("secret-body");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void failedStageDoesNotBecomeSuccessfulOrLeakIntoNextRequest() throws Exception {
        assertThatThrownBy(() -> filter.doFilter(request(), new MockHttpServletResponse(), (req, res) ->
                ListingRequestTiming.measure("images", () -> {
                    throw new IllegalStateException("private exception message");
                }))).isInstanceOf(IllegalStateException.class);

        Map<String, Object> failed = fields(logs.list.getFirst());
        assertThat(failed).containsEntry("outcome", "exception").containsEntry("status", null)
                .containsEntry("images_completed", false).containsEntry("response_complete", false)
                .doesNotContainKey("mapping_ms");
        assertThat(failed.toString()).doesNotContain("private exception message");
        ListingRequestTiming.count("should_not_leak", 1);
        filter.doFilter(request(), new MockHttpServletResponse(), (req, res) -> { });
        assertThat(fields(logs.list.getLast())).doesNotContainKeys("images_ms", "should_not_leak");
    }

    @Test
    void sendErrorAndRedispatchAreNotCountedAsTwoCompletedRequests() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).sendError(502));
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, (req, res) -> { });
        assertThat(logs.list).hasSize(1);
        assertThat(fields(logs.list.getFirst())).containsEntry("status", 502)
                .containsEntry("outcome", "send_error").containsEntry("response_complete", false);
    }

    @Test
    void asyncHandoffIsExplicitlyIncompleteAndRedispatchIsSkipped() throws Exception {
        MockHttpServletRequest request = request();
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> req.startAsync());
        request.setDispatcherType(DispatcherType.ASYNC);
        filter.doFilter(request, response, (req, res) -> { });
        assertThat(logs.list).hasSize(1);
        assertThat(fields(logs.list.getFirst())).containsEntry("outcome", "async_started")
                .containsEntry("status", null).containsEntry("response_complete", false);
    }

    @Test
    void skipsOtherPathsMethodsAndDisabledServiceCalls() throws Exception {
        for (String path : new String[]{"/api/listings/1", "/actuator/health", "/api/delivery/requests/events"}) {
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), (req, res) -> { });
        }
        filter.doFilter(new MockHttpServletRequest("POST", "/api/listings"), new MockHttpServletResponse(), (req, res) -> { });
        assertThat(ListingRequestTiming.measure("listings", () -> 42)).isEqualTo(42);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void requiresBothDevProfileAndExplicitEnabledProperty() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(RequestIdConfiguration.class);
        runner.run(context -> assertThat(context).doesNotHaveBean("listingRequestTimingFilter"));
        runner.withPropertyValues("setty.observability.listing-timing.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean("listingRequestTimingFilter"));
        runner.withPropertyValues("spring.profiles.active=dev", "setty.observability.listing-timing.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("listingRequestTimingFilter"));
        runner.withPropertyValues("spring.profiles.active=dev", "setty.observability.listing-timing.enabled=true")
                .run(context -> assertThat(context).hasBean("listingRequestTimingFilter"));
    }

    @Test
    void concurrentRequestsKeepTheirOwnCountsAndIds() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentRequest(11, barrier));
            var second = executor.submit(() -> concurrentRequest(22, barrier));
            String firstId = first.get(5, TimeUnit.SECONDS);
            String secondId = second.get(5, TimeUnit.SECONDS);
            assertThat(logs.list).hasSize(2);
            assertThat(logs.list).anySatisfy(event -> {
                assertThat(event.getMDCPropertyMap()).containsEntry("requestId", firstId);
                assertThat(fields(event)).containsEntry("listing_count", 11);
            }).anySatisfy(event -> {
                assertThat(event.getMDCPropertyMap()).containsEntry("requestId", secondId);
                assertThat(fields(event)).containsEntry("listing_count", 22);
            });
        }
    }

    private String concurrentRequest(int count, CyclicBarrier barrier) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request(), response, (req, res) -> filter.doFilter(req, res, (r, s) -> {
            ListingRequestTiming.count("listing_count", count);
            try {
                barrier.await(3, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new ServletException(exception);
            }
        }));
        return response.getHeader("X-Request-Id");
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/listings");
    }

    static Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        event.getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
        return fields;
    }
}
