package setty.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.delivery.application.BundleCandidateService;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.DeliveryRequestsChanged;
import setty.delivery.application.RegisterDeliveryService;
import setty.delivery.application.readmodel.DeliveryRequest;
import setty.delivery.domain.DeliveryId;
import setty.delivery.domain.DriverId;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.delivery.domain.GridCell;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DeliveryBundleApiTest.ListenerAfterCache.class)
class DeliveryBundleApiTest extends MySqlIntegrationTestSupport {

    private static final double HERE_LATITUDE = 37.4985;
    private static final double HERE_LONGITUDE = 126.9520;
    private static final GridCell HERE = GridCell.locate(HERE_LATITUDE, HERE_LONGITUDE).orElseThrow();
    private static final GridCell MY_DESTINATION = GridCell.locate(37.5665, 126.9780).orElseThrow();
    private static final GridCell NEAR_MY_DESTINATION =
            new GridCell(MY_DESTINATION.column() + 1, MY_DESTINATION.row() - 1);
    private static final GridCell FAR_FROM_MY_DESTINATION =
            new GridCell(MY_DESTINATION.column() + 3, MY_DESTINATION.row());
    private static final DriverId DRIVER = new DriverId(2L);
    private static final DriverId OTHER_DRIVER = new DriverId(3L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisterDeliveryService registerDeliveryService;

    @Autowired
    private DeliveryLifecycleService deliveryLifecycleService;

    @Autowired
    private BundleCandidateService bundleCandidateService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ListenerAfterCache listenerAfterCache;

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
        // 테스트끼리 컨텍스트를 함께 쓰므로 지운 행이 칸 캐시에 남지 않게 DB와 맞춘다.
        bundleCandidateService.reconcile();
    }

    @Test
    void 위치를_보내면_칸을_돌려주고_내_도착_칸_근처로_가는_콜만_후보로_준다() throws Exception {
        final DeliveryId nearby = myRouteWithCandidates();

        sendLocation("driver-token", HERE_LATITUDE, HERE_LONGITUDE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cell").value(HERE.code()));

        bundle("driver-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cell").value(HERE.code()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].deliveryId").value(nearby.value()))
                .andExpect(jsonPath("$.items[0].deliveryFee").value(20_000))
                .andExpect(jsonPath("$.items[0].status").value("REQUESTED"));
        // 기존 대기 목록은 그대로 두 건을 모두 보여 준다.
        mockMvc.perform(get("/api/delivery/requests").header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 목록_변경을_받은_다른_리스너는_이미_갱신된_후보를_본다() throws Exception {
        final DeliveryId mine = register(201L, 10_000, new GridCell(HERE.column() - 5, HERE.row()), MY_DESTINATION);
        deliveryLifecycleService.accept(mine, DRIVER, Instant.now());
        sendLocation("driver-token", HERE_LATITUDE, HERE_LONGITUDE);
        listenerAfterCache.seen().clear();

        final DeliveryId nearby = register(202L, 20_000, HERE, NEAR_MY_DESTINATION);

        assertThat(listenerAfterCache.seen()).containsExactly(List.of(nearby.value()));
    }

    @Test
    void 진행_중_배차가_없는_배송원은_같은_칸에_있어도_후보가_없다() throws Exception {
        myRouteWithCandidates();

        sendLocation("other-driver-token", HERE_LATITUDE, HERE_LONGITUDE).andExpect(status().isOk());

        bundle("other-driver-token")
                .andExpect(jsonPath("$.cell").value(HERE.code()))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 다른_배송원이_수락하면_빠지고_배차를_취소하면_다시_후보가_된다() throws Exception {
        final DeliveryId nearby = myRouteWithCandidates();
        sendLocation("driver-token", HERE_LATITUDE, HERE_LONGITUDE);

        deliveryLifecycleService.accept(nearby, OTHER_DRIVER, Instant.now());
        bundle("driver-token").andExpect(jsonPath("$.items.length()").value(0));

        deliveryLifecycleService.cancelAssignment(nearby, OTHER_DRIVER, Instant.now());
        bundle("driver-token").andExpect(jsonPath("$.items[0].deliveryId").value(nearby.value()));
    }

    @Test
    void 구매자가_주문을_취소하면_후보에서_빠진다() throws Exception {
        myRouteWithCandidates();
        sendLocation("driver-token", HERE_LATITUDE, HERE_LONGITUDE);

        deliveryLifecycleService.cancel(OrderId.from(202L), "cancel-202", Instant.now());

        bundle("driver-token").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void 위도나_경도가_없거나_격자_밖이면_400이다() throws Exception {
        mockMvc.perform(put("/api/delivery/location")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer driver-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":37.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        sendLocation("driver-token", -33.8, 151.2)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 토큰이_없으면_위치를_받지_않는다() throws Exception {
        mockMvc.perform(put("/api/delivery/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":37.5,\"lng\":126.9}"))
                .andExpect(status().isUnauthorized());
    }

    // 내 배차: 다른 곳 → MY_DESTINATION. 대기: HERE → 도착 이웃 칸(2만 원), HERE → 먼 칸(3만 원).
    private DeliveryId myRouteWithCandidates() {
        final DeliveryId mine = register(201L, 10_000, new GridCell(HERE.column() - 5, HERE.row()), MY_DESTINATION);
        deliveryLifecycleService.accept(mine, DRIVER, Instant.now());
        final DeliveryId nearby = register(202L, 20_000, HERE, NEAR_MY_DESTINATION);
        register(203L, 30_000, HERE, FAR_FROM_MY_DESTINATION);
        return nearby;
    }

    private DeliveryId register(final long orderId, final int fee, final GridCell pickup, final GridCell destination) {
        registerDeliveryService.register(
                OrderId.from(orderId),
                FurnitureInfo.of("가상 원목 의자", "CHAIR"),
                DeliveryRoute.of(
                        DeliveryPoint.pickup("서울시 가상구 출발로 1", "010-0000-0001", null, null, pickup.code()),
                        DeliveryPoint.destination("서울시 가상구 도착로 2", "010-0000-0002", null, null,
                                destination.code())
                ),
                EstimatedDeliveryFee.from(fee),
                Instant.now()
        );
        return new DeliveryId(jdbcTemplate.queryForObject(
                "SELECT id FROM delivery WHERE order_id = ?", Long.class, orderId));
    }

    private ResultActions sendLocation(final String token, final double latitude, final double longitude)
            throws Exception {
        return mockMvc.perform(put("/api/delivery/location")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\":" + latitude + ",\"lng\":" + longitude + "}"));
    }

    private ResultActions bundle(final String token) throws Exception {
        return mockMvc.perform(get("/api/delivery/requests/bundle")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // 칸 캐시 갱신 바로 다음 순서로 목록 변경을 받는 리스너. 기존 SSE 알림(기본 순서)도 이보다 늦으므로,
    // 알림을 받은 웹이 후보를 다시 불러오는 상황을 가장 이른 자리에서 흉내 낸다.
    static class ListenerAfterCache {

        private final List<List<Long>> seen = new CopyOnWriteArrayList<>();

        // 빈이 프록시로 감싸여 필드를 직접 읽으면 프록시의 빈 필드를 보므로 메서드로 읽는다.
        List<List<Long>> seen() {
            return seen;
        }

        @Autowired
        private BundleCandidateService bundleCandidateService;

        @Order(Ordered.HIGHEST_PRECEDENCE + 1)
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(final DeliveryRequestsChanged event) {
            seen.add(bundleCandidateService.findCandidates(DRIVER).requests().stream()
                    .map(DeliveryRequest.Summary::deliveryId)
                    .toList());
        }
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
