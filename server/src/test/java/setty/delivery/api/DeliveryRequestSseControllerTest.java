package setty.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.DeliveryQueryService;
import setty.delivery.auth.domain.DeliveryMember;
import setty.delivery.domain.DriverId;
import setty.global.auth.LoginDeliveryMember;

class DeliveryRequestSseControllerTest {

    private final List<MvcResult> connections = new ArrayList<>();
    private final DeliveryMember member = mock(DeliveryMember.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private DeliveryRequestEventStream stream;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stream = new DeliveryRequestEventStream(3,
                () -> new SseEmitter(DeliveryRequestEventStream.CONNECTION_TIMEOUT_MILLIS), meterRegistry);
        final DeliveryController controller = new DeliveryController(
                mock(DeliveryQueryService.class), mock(DeliveryLifecycleService.class), stream);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new DeliveryExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(final MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(LoginDeliveryMember.class);
                    }

                    @Override
                    public Object resolveArgument(
                            final MethodParameter parameter,
                            final ModelAndViewContainer container,
                            final NativeWebRequest request,
                            final WebDataBinderFactory binderFactory
                    ) {
                        return member;
                    }
                })
                .build();
    }

    @AfterEach
    void tearDown() {
        connections.forEach(connection -> connection.getRequest().getAsyncContext().complete());
        stream.close();
        meterRegistry.close();
    }

    @Test
    void driverLimitReturns429ForAnSseRequestAndAllowsReconnectionAfterCleanup() throws Exception {
        final MvcResult first = connect(1L);
        connect(1L);

        mockMvc.perform(get("/api/delivery/requests/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isTooManyRequests())
                .andExpect(request().asyncNotStarted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("DRIVER_LIMIT_EXCEEDED"));
        assertThat(stream.subscriberCount(new DriverId(1L))).isEqualTo(2);

        first.getRequest().getAsyncContext().complete();
        connect(1L);
        assertThat(stream.subscriberCount(new DriverId(1L))).isEqualTo(2);
    }

    @Test
    void totalLimitReturns503ForAnSseRequestAndAllowsRegistrationAfterCleanup() throws Exception {
        final MvcResult first = connect(1L);
        connect(2L);
        connect(3L);
        when(member.getId()).thenReturn(4L);

        mockMvc.perform(get("/api/delivery/requests/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isServiceUnavailable())
                .andExpect(request().asyncNotStarted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("TOTAL_LIMIT_EXCEEDED"));
        assertThat(stream.subscriberCount()).isEqualTo(3);

        first.getRequest().getAsyncContext().complete();
        connect(4L);
        assertThat(stream.subscriberCount()).isEqualTo(3);
        assertThat(stream.subscriberCount(new DriverId(1L))).isZero();
        assertThat(stream.subscriberCount(new DriverId(4L))).isOne();
    }

    @Test
    void closedStreamReturns503BeforeStartingAnSseResponse() throws Exception {
        stream.close();
        when(member.getId()).thenReturn(1L);

        mockMvc.perform(get("/api/delivery/requests/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isServiceUnavailable())
                .andExpect(request().asyncNotStarted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("STREAM_CLOSED"));
        assertThat(stream.subscriberCount()).isZero();
    }

    private MvcResult connect(final long driverId) throws Exception {
        when(member.getId()).thenReturn(driverId);
        final MvcResult connection = mockMvc.perform(get("/api/delivery/requests/events")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        connections.add(connection);
        assertThat(connection.getResponse().getContentAsString()).contains("event:connected");
        return connection;
    }
}
