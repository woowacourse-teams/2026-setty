package setty.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import setty.delivery.application.AvailableRequestCache;
import setty.delivery.application.DeliveryLifecycleService;
import setty.delivery.application.DeliveryRequestsChanged;
import setty.delivery.application.RegisterDeliveryService;
import setty.delivery.application.readmodel.AvailableRequests;
import setty.delivery.domain.OrderId;
import setty.delivery.domain.delivery.DeliveryPoint;
import setty.delivery.domain.delivery.DeliveryRoute;
import setty.delivery.domain.delivery.EstimatedDeliveryFee;
import setty.delivery.domain.delivery.FurnitureInfo;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 테스트 설정은 캐시를 끄므로, 캐시를 켠 상태에서 목록 변경이 다음 조회에 반영되는지 확인한다. */
@SpringBootTest(properties = "setty.delivery.request-cache.enabled=true")
@AutoConfigureMockMvc
@Import(DeliveryRequestCacheIntegrationTest.NotificationProbeConfig.class)
class DeliveryRequestCacheIntegrationTest extends MySqlIntegrationTestSupport {

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RegisterDeliveryService registerDeliveryService;

    @Autowired
    private DeliveryLifecycleService deliveryLifecycleService;

    @Autowired
    private AvailableRequestCache availableRequestCache;

    @Autowired
    private NotificationProbe notificationProbe;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO delivery_member (id, login_id, password, phone_number, license_plate_number, car_type,
                                             business_registration_number, token)
                VALUES (2, 'cache-driver', 'encoded-password', '010-0000-0000', '12가3456', '다마스', '000-00-00000',
                        'driver-token')
                """);
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM delivery_member");
        availableRequestCache.invalidateAll();
    }

    @Test
    void 등록_수락_배정_취소_주문_취소가_저장된_목록을_비우고_다음_조회에_반영된다() throws Exception {
        register(1L, BASE);
        final long first = deliveryIdOf(1L);
        assertListed(List.of(first));

        // 이벤트를 거치지 않은 변경은 저장된 목록이 그대로 응답된다. 아래 단계가 실제로 캐시를 거친다는 확인이다.
        jdbcTemplate.update("UPDATE delivery SET item_name = '이벤트 없는 변경' WHERE id = ?", first);
        assertThat(read(get("/api/delivery/requests")).get(0).get("itemName").asString())
                .isNotEqualTo("이벤트 없는 변경");

        register(2L, BASE.plusSeconds(1));
        final long second = deliveryIdOf(2L);
        assertListed(List.of(second, first));
        assertThat(read(get("/api/delivery/requests")).get(1).get("itemName").asString())
                .isEqualTo("이벤트 없는 변경");

        mockMvc.perform(post("/api/delivery/requests/{deliveryId}", second)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(status().isNoContent());
        assertListed(List.of(first));

        mockMvc.perform(post("/api/delivery/shipments/{deliveryId}/cancellation", second)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(status().isNoContent());
        assertListed(List.of(second, first));

        deliveryLifecycleService.cancel(OrderId.from(2L), "cache-cancellation", BASE.plusSeconds(2));
        assertListed(List.of(first));
    }

    @Test
    void 순서를_정하지_않은_커밋_후_리스너보다_먼저_비운다() throws Exception {
        register(1L, BASE);
        availableRequestCache.getFirstPage(NotificationProbe.PAGE_SIZE,
                () -> new AvailableRequests(List.of(), Map.of(), "before-change"));
        notificationProbe.reloaded().clear();

        register(2L, BASE.plusSeconds(1));

        assertThat(notificationProbe.reloaded()).containsExactly(true);
    }

    @Test
    void SSE_알림_리스너는_순서를_정하지_않아_캐시_무효화_뒤에_실행된다() throws Exception {
        final Method notification = DeliveryRequestEventStream.class.getMethod("on", DeliveryRequestsChanged.class);
        final Method invalidation = AvailableRequestCache.class.getMethod("on", DeliveryRequestsChanged.class);

        assertThat(AnnotationUtils.findAnnotation(notification, Order.class)).isNull();
        assertThat(AnnotationUtils.findAnnotation(invalidation, Order.class).value())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    private void assertListed(final List<Long> expected) throws Exception {
        assertThat(ids(read(get("/api/delivery/requests")))).containsExactlyElementsOf(expected);
        assertThat(ids(read(get("/api/delivery/requests").param("size", "20")).get("items")))
                .containsExactlyElementsOf(expected);
    }

    private void register(final long orderId, final Instant requestedAt) {
        registerDeliveryService.register(
                OrderId.from(orderId),
                FurnitureInfo.of("가상 원목 의자", "CHAIR"),
                DeliveryRoute.of(
                        DeliveryPoint.pickup("서울시 가상구 출발로 1", "010-0000-0001", null, null),
                        DeliveryPoint.destination("서울시 가상구 도착로 2", "010-0000-0002", null, null)
                ),
                EstimatedDeliveryFee.from(10_000),
                requestedAt
        );
    }

    private long deliveryIdOf(final long orderId) {
        return jdbcTemplate.queryForObject("SELECT id FROM delivery WHERE order_id = ?", Long.class, orderId);
    }

    private List<Long> ids(final JsonNode items) {
        final List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("deliveryId").asLong()));
        return ids;
    }

    private JsonNode read(final MockHttpServletRequestBuilder request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request
                        .header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @TestConfiguration
    static class NotificationProbeConfig {

        @Bean
        NotificationProbe notificationProbe(final AvailableRequestCache availableRequestCache) {
            return new NotificationProbe(availableRequestCache);
        }
    }

    /**
     * DeliveryRequestEventStream#on처럼 순서를 정하지 않은 커밋 후 리스너. 실행 시점에 캐시가 비었는지 기록한다.
     * 다른 검증이 읽는 키를 채우지 않도록 테스트에서만 쓰는 페이지 크기로 확인한다.
     */
    static class NotificationProbe {

        static final int PAGE_SIZE = 1;

        private final AvailableRequestCache availableRequestCache;
        private final List<Boolean> reloaded = new CopyOnWriteArrayList<>();

        NotificationProbe(final AvailableRequestCache availableRequestCache) {
            this.availableRequestCache = availableRequestCache;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void on(final DeliveryRequestsChanged event) {
            final AtomicBoolean loaded = new AtomicBoolean();
            availableRequestCache.getFirstPage(PAGE_SIZE, () -> {
                loaded.set(true);
                return new AvailableRequests(List.of(), Map.of(), null);
            });
            reloaded.add(loaded.get());
        }

        // 리스너 빈은 이벤트 발행 기록용 프록시로 감싸지므로 필드 대신 메서드로 읽는다.
        public List<Boolean> reloaded() {
            return reloaded;
        }
    }
}
