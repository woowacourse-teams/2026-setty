package setty.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.RegisterDeliveryService;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryAssignmentCancellationApiTest extends MySqlIntegrationTestSupport {

    private static final long ORDER_ID = 101L;
    private static final DriverId DRIVER_ID = new DriverId(2L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisterDeliveryService registerDeliveryService;

    @Autowired
    private DeliveryLifecycleService deliveryLifecycleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertDriver(2L, "driver-token");
        insertDriver(3L, "other-driver-token");
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM delivery_member");
    }

    @Test
    void 담당_기사가_취소하면_204이고_대기로_돌아간다() throws Exception {
        final DeliveryId deliveryId = acceptedDelivery();

        cancel(deliveryId, "driver-token")
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(column("status")).isEqualTo("REQUESTED");
        assertThat(column("driver_id")).isNull();
        assertThat(column("accepted_at")).isNull();
    }

    @Test
    void 다른_기사가_취소하면_404이고_배차가_유지된다() throws Exception {
        final DeliveryId deliveryId = acceptedDelivery();

        cancel(deliveryId, "other-driver-token")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELIVERY_DRIVER_MISMATCH"));

        assertThat(column("status")).isEqualTo("ACCEPTED");
    }

    @Test
    void 픽업_뒤에는_409다() throws Exception {
        final DeliveryId deliveryId = acceptedDelivery();
        deliveryLifecycleService.pickUp(deliveryId, DRIVER_ID, Instant.now());

        cancel(deliveryId, "driver-token")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DELIVERY_TRANSITION"));

        assertThat(column("status")).isEqualTo("PICKED_UP");
    }

    @Test
    void 두_번_취소하면_두_번째는_409다() throws Exception {
        final DeliveryId deliveryId = acceptedDelivery();
        cancel(deliveryId, "driver-token").andExpect(status().isNoContent());

        cancel(deliveryId, "driver-token")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_DELIVERY_TRANSITION"));
    }

    private ResultActions cancel(final DeliveryId deliveryId, final String token) throws Exception {
        return mockMvc.perform(post("/api/delivery/shipments/{deliveryId}/cancellation", deliveryId.value())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private DeliveryId acceptedDelivery() {
        registerDeliveryService.register(
                OrderId.from(ORDER_ID),
                FurnitureInfo.of("가상 원목 의자", "CHAIR"),
                DeliveryRoute.of(
                        DeliveryPoint.pickup("서울시 가상구 출발로 1", "010-0000-0001", null, null),
                        DeliveryPoint.destination("서울시 가상구 도착로 2", "010-0000-0002", null, null)
                ),
                EstimatedDeliveryFee.from(10_000),
                Instant.now()
        );
        final DeliveryId deliveryId = new DeliveryId(jdbcTemplate.queryForObject(
                "SELECT id FROM delivery WHERE order_id = ?", Long.class, ORDER_ID
        ));
        deliveryLifecycleService.accept(deliveryId, DRIVER_ID, Instant.now());
        return deliveryId;
    }

    private Object column(final String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM delivery WHERE order_id = ?", Object.class, ORDER_ID
        );
    }

    private void insertDriver(final long id, final String token) {
        jdbcTemplate.update(
                """
                INSERT INTO delivery_member (id, login_id, password, phone_number, license_plate_number, car_type,
                                             business_registration_number, token)
                VALUES (?, ?, 'encoded-password', '010-0000-0000', '12가3456', '다마스', '000-00-00000', ?)
                """,
                id,
                "driver" + id,
                token
        );
    }
}
