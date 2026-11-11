package setty.notification.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import setty.common.DriverSettlementConfirmed;
import setty.common.SettlementConfirmed;
import setty.notification.order.application.OrderNotificationDispatcher;
import setty.notification.order.application.OrderNotificationService;
import setty.notification.owner.NotificationOwner;
import setty.notification.send.NotificationMessage;
import setty.notification.send.NotificationSender;
import setty.notification.send.SendResult;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
class DriverNotificationIntegrationTest extends MySqlIntegrationTestSupport {

    // 회원과 기사가 같은 번호를 갖게 해 서로의 알림이 섞이지 않는지 본다.
    private static final long SHARED_ID = 701L;
    private static final long LISTING_ID = 71L;
    private static final long ORDER_ID = 7_001L;
    private static final int DELIVERY_FEE = 15_000;
    private static final Instant CONFIRMED_AT = Instant.parse("2026-11-11T01:00:00.123456789Z");
    private static final String DRIVER_TOKEN = "fcm-driver-" + "d".repeat(40);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OrderNotificationDispatcher dispatcher;

    @Autowired
    private OrderNotificationService orderNotificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationSender notificationSender;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(SHARED_ID);
        insertDriver(SHARED_ID);
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '원목 4인 식탁', '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, 'SOLD', true, NOW(6), NOW(6), NULL)
                """,
                LISTING_ID, SHARED_ID);
        reset(notificationSender);
        when(notificationSender.send(anyList())).thenReturn(SendResult.allSucceeded());
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM order_notifications");
        jdbcTemplate.update("DELETE FROM device_tokens");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
        jdbcTemplate.update("DELETE FROM delivery_member");
    }

    @Test
    void 기사_정산이_확정되면_기사에게_배송비와_함께_알린다() throws Exception {
        publish(driverSettlementConfirmed());

        assertThat(rows()).containsExactly("DRIVER_SETTLEMENT_CONFIRMED:DRIVER:" + SHARED_ID + ":DRIVER:SENT");
        assertThat(jdbcTemplate.queryForObject("SELECT amount FROM order_notifications", Integer.class))
                .isEqualTo(DELIVERY_FEE);
        assertThat(jdbcTemplate.queryForObject("SELECT listing_id FROM order_notifications", Long.class)).isNull();
        final ArgumentCaptor<List<NotificationMessage>> captor = messagesCaptor();
        verify(notificationSender).send(captor.capture());
        final NotificationMessage message = captor.getValue().get(0);
        assertThat(message.owner()).isEqualTo(NotificationOwner.driver(SHARED_ID));
        assertThat(message.listingId()).isNull();
        assertThat(message.order().orderId()).isEqualTo(ORDER_ID);
        assertThat(message.order().kind()).isEqualTo("DRIVER_SETTLEMENT_CONFIRMED");
        assertThat(message.order().recipient()).isEqualTo("DRIVER");
        assertThat(message.order().headline()).isEqualTo("배송비 정산이 확정됐어요");
        assertThat(message.order().body()).isEqualTo("15,000원");
        mockMvc.perform(get("/api/delivery/notifications").header(HttpHeaders.AUTHORIZATION, driverBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("DRIVER_SETTLEMENT_CONFIRMED"))
                .andExpect(jsonPath("$.items[0].headline").value("배송비 정산이 확정됐어요"))
                .andExpect(jsonPath("$.items[0].amount").value(DELIVERY_FEE))
                .andExpect(jsonPath("$.items[0].orderId").value(ORDER_ID))
                .andExpect(jsonPath("$.items[0].read").value(false));
    }

    @Test
    void 같은_기사_정산_확정이_다시_와도_알림은_한_번만_만들고_보낸다() {
        dispatcher.dispatch(driverSettlementConfirmed());
        dispatcher.dispatch(driverSettlementConfirmed());

        assertThat(rows()).hasSize(1);
        verify(notificationSender).send(anyList());
    }

    @Test
    void 같은_번호의_회원과_기사는_서로의_알림을_보지도_읽지도_지우지도_못한다() throws Exception {
        dispatcher.dispatch(new SettlementConfirmed(ORDER_ID, LISTING_ID, SHARED_ID, 150_000, CONFIRMED_AT));
        dispatcher.dispatch(driverSettlementConfirmed());
        final long memberRowId = idOf("SETTLEMENT_CONFIRMED");
        final long driverRowId = idOf("DRIVER_SETTLEMENT_CONFIRMED");

        mockMvc.perform(get("/api/me/notifications").param("include", "ORDER")
                        .header(HttpHeaders.AUTHORIZATION, memberBearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("SETTLEMENT_CONFIRMED"));
        mockMvc.perform(get("/api/me/notifications/unread-count").param("include", "ORDER")
                        .header(HttpHeaders.AUTHORIZATION, memberBearer()))
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/delivery/notifications").header(HttpHeaders.AUTHORIZATION, driverBearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("DRIVER_SETTLEMENT_CONFIRMED"));

        mockMvc.perform(post("/api/me/notifications/orders/{id}/read", driverRowId)
                        .header(HttpHeaders.AUTHORIZATION, memberBearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/delivery/notifications/{id}/read", memberRowId)
                        .header(HttpHeaders.AUTHORIZATION, driverBearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/me/notifications/read").param("include", "ORDER")
                        .header(HttpHeaders.AUTHORIZATION, memberBearer()))
                .andExpect(status().isNoContent());
        assertThat(readAtIsNull(driverRowId)).isTrue();

        mockMvc.perform(post("/api/delivery/notifications/{id}/read", driverRowId)
                        .header(HttpHeaders.AUTHORIZATION, driverBearer()))
                .andExpect(status().isNoContent());
        assertThat(readAtIsNull(driverRowId)).isFalse();

        orderNotificationService.deleteAllOf(NotificationOwner.member(SHARED_ID));
        assertThat(rows()).containsExactly("DRIVER_SETTLEMENT_CONFIRMED:DRIVER:" + SHARED_ID + ":DRIVER:SENT");
    }

    @Test
    void 기사는_자기_로그인으로_기기_토큰을_등록하고_해제한다() throws Exception {
        mockMvc.perform(put("/api/delivery/device-token").header(HttpHeaders.AUTHORIZATION, driverBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + DRIVER_TOKEN + "\",\"platform\":\"WEB\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT CONCAT(owner_type, ':', member_id, ':', platform) FROM device_tokens", String.class))
                .isEqualTo("DRIVER:" + SHARED_ID + ":WEB");

        mockMvc.perform(delete("/api/me/device-token").header(HttpHeaders.AUTHORIZATION, memberBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + DRIVER_TOKEN + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(tokenCount()).isOne();

        mockMvc.perform(delete("/api/delivery/device-token").header(HttpHeaders.AUTHORIZATION, driverBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + DRIVER_TOKEN + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(tokenCount()).isZero();
    }

    @Test
    void 기사_API는_회원_로그인이나_모르는_플랫폼을_받지_않는다() throws Exception {
        mockMvc.perform(put("/api/delivery/device-token").header(HttpHeaders.AUTHORIZATION, memberBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + DRIVER_TOKEN + "\",\"platform\":\"WEB\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/delivery/notifications").header(HttpHeaders.AUTHORIZATION, memberBearer()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/delivery/device-token").header(HttpHeaders.AUTHORIZATION, driverBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + DRIVER_TOKEN + "\",\"platform\":\"PAGER\"}"))
                .andExpect(status().isBadRequest());
        assertThat(tokenCount()).isZero();
    }

    private void publish(final Object event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        awaitEventsHandled(jdbcTemplate);
    }

    private static DriverSettlementConfirmed driverSettlementConfirmed() {
        return new DriverSettlementConfirmed(ORDER_ID, SHARED_ID, DELIVERY_FEE, CONFIRMED_AT);
    }

    private List<String> rows() {
        return jdbcTemplate.queryForList(
                "SELECT CONCAT(kind, ':', owner_type, ':', member_id, ':', recipient, ':', status) FROM order_notifications",
                String.class);
    }

    private long idOf(final String kind) {
        return jdbcTemplate.queryForObject("SELECT id FROM order_notifications WHERE kind = ?", Long.class, kind);
    }

    private boolean readAtIsNull(final long notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT read_at IS NULL FROM order_notifications WHERE id = ?", Boolean.class, notificationId);
    }

    private long tokenCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM device_tokens", Long.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<NotificationMessage>> messagesCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token, token_expires_at)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?, TIMESTAMPADD(DAY, 1, UTC_TIMESTAMP(6)))
                """,
                memberId, "member" + memberId, "token-" + memberId);
    }

    private void insertDriver(final long driverId) {
        jdbcTemplate.update(
                """
                INSERT INTO delivery_member (id, login_id, password, phone_number, license_plate_number, car_type,
                                             business_registration_number, token, token_expires_at)
                VALUES (?, ?, 'encoded-password', '010-0000-0000', '12가3456', '다마스', '000-00-00000', ?,
                        TIMESTAMPADD(DAY, 1, UTC_TIMESTAMP(6)))
                """,
                driverId, "driver" + driverId, "driver-token-" + driverId);
    }

    private static String memberBearer() {
        return "Bearer token-" + SHARED_ID;
    }

    private static String driverBearer() {
        return "Bearer driver-token-" + SHARED_ID;
    }
}
