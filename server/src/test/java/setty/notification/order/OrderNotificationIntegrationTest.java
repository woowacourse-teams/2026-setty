package setty.notification.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import setty.common.OrderStatusNotified;
import setty.common.OrderStatusNotified.Change;
import setty.notification.order.application.OrderNotificationDispatcher;
import setty.notification.send.NotificationMessage;
import setty.notification.send.NotificationSender;
import setty.notification.send.SendResult;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@AutoConfigureMockMvc
class OrderNotificationIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 701L;
    private static final long BUYER_ID = 702L;
    private static final long LISTING_ID = 71L;
    private static final long ORDER_ID = 7_001L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-06T01:00:00.123456789Z");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private OrderNotificationDispatcher dispatcher;

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
        insertMember(SELLER_ID);
        insertMember(BUYER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '원목 4인 식탁', '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', true, NOW(6), NOW(6), NULL)
                """,
                LISTING_ID, SELLER_ID);
        reset(notificationSender);
        when(notificationSender.send(anyList())).thenReturn(SendResult.allSucceeded());
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM order_notifications");
        jdbcTemplate.update("DELETE FROM listing_notifications");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 결제_확정은_구매자와_판매자에게_각자의_알림을_보낸다() {
        publish(event(Change.CONFIRMED, OCCURRED_AT));

        assertThat(rows()).containsExactlyInAnyOrder(
                "ORDER_CONFIRMED:" + BUYER_ID + ":BUYER:SENT",
                "LISTING_SOLD:" + SELLER_ID + ":SELLER:SENT");
        final ArgumentCaptor<List<NotificationMessage>> captor = messagesCaptor();
        verify(notificationSender).send(captor.capture());
        assertThat(captor.getValue()).allSatisfy(message -> {
            assertThat(message.title()).isNull();
            assertThat(message.order().orderId()).isEqualTo(ORDER_ID);
            assertThat(message.order().body()).isEqualTo("원목 4인 식탁");
        });
        assertThat(captor.getValue()).extracting(message -> message.order().headline())
                .containsExactlyInAnyOrder("결제가 끝났어요. 기사를 찾고 있어요", "내 물건이 팔렸어요");
    }

    @Test
    void 같은_이벤트가_다시_와도_알림은_한_번만_만들고_보낸다() {
        dispatcher.dispatch(event(Change.DELIVERY_ACCEPTED, OCCURRED_AT));
        dispatcher.dispatch(event(Change.DELIVERY_ACCEPTED, OCCURRED_AT));

        assertThat(rows()).hasSize(2);
        verify(notificationSender).send(anyList());
    }

    @Test
    void 재배차는_수락_시각이_달라_다시_알린다() {
        dispatcher.dispatch(event(Change.DELIVERY_ACCEPTED, OCCURRED_AT));
        dispatcher.dispatch(event(Change.DELIVERY_ACCEPTED, OCCURRED_AT.plusSeconds(120)));

        assertThat(rows()).hasSize(4);
    }

    @Test
    void 발송이_세_번_실패하면_FAILED로_멈추고_예외를_더_던지지_않는다() {
        when(notificationSender.send(anyList())).thenAnswer(invocation -> SendResult.allFailed(invocation.getArgument(0)));
        final OrderStatusNotified event = event(Change.DELIVERY_PICKED_UP, OCCURRED_AT);

        assertThatThrownBy(() -> dispatcher.dispatch(event)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> dispatcher.dispatch(event)).isInstanceOf(IllegalStateException.class);
        dispatcher.dispatch(event);
        dispatcher.dispatch(event);

        assertThat(rows()).containsExactly("DELIVERY_PICKED_UP:" + BUYER_ID + ":BUYER:FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT attempts FROM order_notifications", Integer.class)).isEqualTo(3);
    }

    @Test
    void 같은_이벤트를_동시에_처리해도_행은_받는_사람마다_하나다() {
        final CountDownLatch start = new CountDownLatch(1);
        final OrderStatusNotified event = event(Change.DELIVERY_DELIVERED, OCCURRED_AT);
        final List<CompletableFuture<Void>> runs = List.of(
                CompletableFuture.runAsync(() -> awaitThenDispatch(start, event)),
                CompletableFuture.runAsync(() -> awaitThenDispatch(start, event)));
        start.countDown();
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).join();

        assertThat(rows()).containsExactlyInAnyOrder(
                "DELIVERY_DELIVERED:" + BUYER_ID + ":BUYER:SENT",
                "DELIVERY_DELIVERED:" + SELLER_ID + ":SELLER:SENT");
    }

    @Test
    void 알림함은_include_ORDER일_때만_주문_알림을_시각순으로_섞는다() throws Exception {
        insertKeywordNotification(8_001L, BUYER_ID, "2026-11-06 10:00:01");
        dispatcher.dispatch(event(Change.CONFIRMED, OCCURRED_AT));
        jdbcTemplate.update("UPDATE order_notifications SET created_at = '2026-11-06 10:00:00'");
        dispatcher.dispatch(event(Change.DELIVERY_ACCEPTED, OCCURRED_AT.plusSeconds(60)));
        jdbcTemplate.update("UPDATE order_notifications SET created_at = '2026-11-06 10:00:02' WHERE kind = 'DELIVERY_ACCEPTED'");

        mockMvc.perform(get("/api/me/notifications").header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("KEYWORD"));
        mockMvc.perform(get("/api/me/notifications").param("include", "ORDER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].kind").value("DELIVERY_ACCEPTED"))
                .andExpect(jsonPath("$.items[0].orderId").value(ORDER_ID))
                .andExpect(jsonPath("$.items[0].recipient").value("BUYER"))
                .andExpect(jsonPath("$.items[0].headline").value("기사가 배정됐어요"))
                .andExpect(jsonPath("$.items[0].title").value("원목 4인 식탁"))
                .andExpect(jsonPath("$.items[1].type").value("KEYWORD"))
                .andExpect(jsonPath("$.items[2].kind").value("ORDER_CONFIRMED"));
        mockMvc.perform(get("/api/me/notifications").param("include", "ORDER").param("size", "2").param("page", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("ORDER_CONFIRMED"));
    }

    @Test
    void 안_읽은_수와_읽음은_include_ORDER일_때만_주문_알림까지_다룬다() throws Exception {
        insertKeywordNotification(8_001L, BUYER_ID, "2026-11-06 10:00:01");
        dispatcher.dispatch(event(Change.CONFIRMED, OCCURRED_AT));
        dispatcher.dispatch(event(Change.DELIVERY_PICKED_UP, OCCURRED_AT.plusSeconds(60)));
        final long pickedUpId = jdbcTemplate.queryForObject(
                "SELECT id FROM order_notifications WHERE kind = 'DELIVERY_PICKED_UP'", Long.class);

        assertThat(unreadCount(null)).isEqualTo(1);
        assertThat(unreadCount("ORDER")).isEqualTo(3);

        mockMvc.perform(post("/api/me/notifications/orders/{id}/read", pickedUpId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/me/notifications/orders/{id}/ack", pickedUpId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/me/notifications/orders/{id}/read", pickedUpId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(SELLER_ID)))
                .andExpect(status().isNotFound());
        assertThat(unreadCount("ORDER")).isEqualTo(2);

        mockMvc.perform(post("/api/me/notifications/read").header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isNoContent());
        assertThat(unreadCount("ORDER")).isEqualTo(1);
        mockMvc.perform(post("/api/me/notifications/read").param("include", "ORDER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID)))
                .andExpect(status().isNoContent());
        assertThat(unreadCount("ORDER")).isZero();
    }

    private void awaitThenDispatch(final CountDownLatch start, final OrderStatusNotified event) {
        try {
            start.await();
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        dispatcher.dispatch(event);
    }

    private void publish(final OrderStatusNotified event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        awaitEventsHandled(jdbcTemplate);
    }

    private static OrderStatusNotified event(final Change change, final Instant occurredAt) {
        return new OrderStatusNotified(ORDER_ID, LISTING_ID, BUYER_ID, SELLER_ID, "원목 4인 식탁", change, occurredAt);
    }

    private long unreadCount(final String include) throws Exception {
        final var request = get("/api/me/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(BUYER_ID));
        if (include != null) {
            request.param("include", include);
        }
        final String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("\\D", ""));
    }

    private List<String> rows() {
        return jdbcTemplate.queryForList(
                "SELECT CONCAT(kind, ':', member_id, ':', recipient, ':', status) FROM order_notifications",
                String.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<NotificationMessage>> messagesCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private void insertKeywordNotification(final long id, final long memberId, final String createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO listing_notifications (id, listing_id, member_id, status, attempts, created_at, sent_at)
                VALUES (?, ?, ?, 'SENT', 1, ?, NOW(6))
                """,
                id, LISTING_ID, memberId, createdAt);
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """,
                memberId, "member" + memberId, "token-" + memberId);
    }

    private static String bearer(final long memberId) {
        return "Bearer token-" + memberId;
    }
}
