package setty.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import setty.common.OrderCancellationRequested;
import setty.common.OrderConfirmed;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryRequestReconnectRecoveryIntegrationTest extends MySqlIntegrationTestSupport {

    private static final String DRIVER_TOKEN = "sse-reconnect-recovery-test-token";
    private static final long REQUESTED_ORDER_ID = 91_001L;
    private static final long ACCEPTED_ORDER_ID = 91_002L;
    private static final long CANCELLED_ORDER_ID = 91_003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DeliveryLifecycleService deliveryLifecycleService;

    @Autowired
    private DeliveryRequestEventStream eventStream;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM delivery_member WHERE token = ?", DRIVER_TOKEN);
        jdbcTemplate.update("DELETE FROM delivery_member WHERE login_id = ?", "sse_recovery");
        jdbcTemplate.update(
                """
                INSERT INTO delivery_member (
                    login_id, password, phone_number, license_plate_number,
                    car_type, business_registration_number, token
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "sse_recovery",
                "test-password-hash",
                "010-0000-0001",
                "00가0000",
                "다마스",
                "000-00-00000",
                DRIVER_TOKEN
        );
    }

    @Test
    void reconnectListReturnsOnlyCurrentlyAcceptableRequestsAfterMissedChanges() throws Exception {
        publishCommitted(orderConfirmed(REQUESTED_ORDER_ID, "재연결 후 남을 요청"));
        publishCommitted(orderConfirmed(ACCEPTED_ORDER_ID, "다른 기사가 수락한 요청"));
        publishCommitted(orderConfirmed(CANCELLED_ORDER_ID, "구매자가 취소한 요청"));

        assertThat(eventStream.subscriberCount()).isZero();

        deliveryLifecycleService.accept(
                deliveryId(ACCEPTED_ORDER_ID),
                new DriverId(902L),
                Instant.now()
        );
        publishCommitted(new OrderCancellationRequested(CANCELLED_ORDER_ID, "cancel-sse-recovery"));
        awaitEventsHandled(jdbcTemplate);

        final SseEmitter reconnected = eventStream.subscribe();
        try {
            final MvcResult result = mockMvc.perform(get("/api/delivery/requests")
                            .header("Authorization", "Bearer " + DRIVER_TOKEN))
                    .andExpect(status().isOk())
                    .andReturn();

            final JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            final Set<Long> requestIds = new HashSet<>();
            body.forEach(request -> requestIds.add(request.path("deliveryId").asLong()));

            assertThat(requestIds).containsExactly(deliveryId(REQUESTED_ORDER_ID).value());
        } finally {
            reconnected.complete();
        }
    }

    private DeliveryId deliveryId(final long orderId) {
        return new DeliveryId(jdbcTemplate.queryForObject(
                "SELECT id FROM delivery WHERE order_id = ?",
                Long.class,
                orderId
        ));
    }

    private void publishCommitted(final Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
        awaitEventsHandled(jdbcTemplate);
    }

    private static OrderConfirmed orderConfirmed(final long orderId, final String itemName) {
        return new OrderConfirmed(
                orderId,
                itemName,
                "CHAIR",
                "서울시 가상구 출발로 1",
                "서울시 가상구 도착로 2",
                10_000,
                "010-0000-0001",
                "010-0000-0002",
                10L,
                1L,
                150_000
        );
    }
}
