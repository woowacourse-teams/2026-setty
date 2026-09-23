package setty.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesServerIdAndRestoresUnrelatedContext() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestIdFilter.HEADER_NAME, "client-supplied-id");
        MDC.put("otherContext", "preserved");

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo(response.getHeader(RequestIdFilter.HEADER_NAME));
            assertThat(MDC.get("otherContext")).isEqualTo("preserved");
        });

        final String requestId = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertThat(UUID.fromString(requestId).version()).isEqualTo(4);
        assertThat(requestId).isNotEqualTo("client-supplied-id");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        assertThat(MDC.get("otherContext")).isEqualTo("preserved");
    }

    @Test
    void cleansUpAfterAnExceptionBeforeTheNextRequestOnTheSameThread() throws Exception {
        final MockHttpServletResponse failedResponse = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), failedResponse, (req, res) -> {
            throw new ServletException("synthetic failure");
        })).isInstanceOf(ServletException.class);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();

        final MockHttpServletResponse nextResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), nextResponse, (req, res) ->
                assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNotEqualTo(
                        failedResponse.getHeader(RequestIdFilter.HEADER_NAME)));

        assertThat(nextResponse.getHeader(RequestIdFilter.HEADER_NAME)).isNotNull();
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void restoresOuterRequestContextAfterNestedRequest() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "outer-request");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) ->
                assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNotEqualTo("outer-request"));

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("outer-request");
    }

    @ParameterizedTest
    @EnumSource(value = DispatcherType.class, names = {"ASYNC", "ERROR"})
    void reusesIdOnRedispatch(final DispatcherType dispatcherType) throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse initialResponse = new MockHttpServletResponse();
        filter.doFilter(request, initialResponse, (req, res) -> { });
        final String requestId = initialResponse.getHeader(RequestIdFilter.HEADER_NAME);

        request.setDispatcherType(dispatcherType);
        if (dispatcherType == DispatcherType.ERROR) {
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/synthetic-error");
        }
        final MockHttpServletResponse redispatchedResponse = new MockHttpServletResponse();
        filter.doFilter(request, redispatchedResponse, (req, res) ->
                assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo(requestId));

        assertThat(redispatchedResponse.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo(requestId);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void preservesIdAndHeaderDuringNestedErrorDispatch() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            final String initialId = MDC.get(RequestIdFilter.MDC_KEY);
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/synthetic-error");
            response.reset();

            filter.doFilter(request, response, (errorRequest, errorResponse) ->
                    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo(initialId));

            assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo(initialId);
            assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo(initialId);
        });

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void isolatesOverlappingRequestsOnDifferentThreads() throws Exception {
        final CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var first = executor.submit(() -> concurrentRequest(barrier));
            final var second = executor.submit(() -> concurrentRequest(barrier));

            assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
        }
    }

    private String concurrentRequest(final CyclicBarrier barrier) throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
            final String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ServletException(exception);
            } catch (final Exception exception) {
                throw new ServletException(exception);
            }
            assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo(requestId);
            assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo(requestId);
        });
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
        return response.getHeader(RequestIdFilter.HEADER_NAME);
    }
}
