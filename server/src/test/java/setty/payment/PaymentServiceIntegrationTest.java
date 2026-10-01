package setty.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;
import static setty.global.event.EventPublicationTestSupport.incompletePublicationCount;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import setty.common.PaymentCompleted;
import setty.common.PaymentFailed;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.application.PaymentService;
import setty.payment.domain.Payment;
import setty.payment.infrastructure.TossConfirmResult;
import setty.payment.infrastructure.TossPaymentClient;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.order.service.PendingOrderExpirationService;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@RecordApplicationEvents
class PaymentServiceIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 101L;
    private static final long BUYER_ID = 202L;
    private static final long LISTING_ID = 11L;
    private static final long ORDER_ID = 500L;
    private static final int PRICE = 150_000;
    private static final int DELIVERY_FEE = 10_000;
    private static final int TOTAL_PRICE = PRICE + DELIVERY_FEE;
    // 토스 orderId는 `<주문id>_<랜덤>` 복합키. 서버는 앞부분에서 ORDER_ID를 추출한다.
    private static final String TOSS_ORDER_ID = ORDER_ID + "_test-token";
    private static final String PAYMENT_KEY = "test_payment_key_1";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PendingOrderExpirationService expirationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(SELLER_ID);
        insertMember(BUYER_ID);
        insertListing(LISTING_ID, SELLER_ID);
        insertPendingOrder(ORDER_ID, LISTING_ID, BUYER_ID);
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 결제_승인에_성공하면_결제가_저장되고_PaymentCompleted가_발행된다() {
        stubTossSuccess();

        final Payment payment = paymentService.confirm(TOSS_ORDER_ID,PAYMENT_KEY, TOTAL_PRICE);

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getStatus().name()).isEqualTo("DONE");
        assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(paymentCount()).isEqualTo(1);
        // payment는 주문·배차를 만들지 않는다 — 결과만 이벤트로 알린다.
        assertThat(events.stream(PaymentCompleted.class).map(PaymentCompleted::orderId))
                .containsExactly(ORDER_ID);
        assertThat(orderStaysUntouched()).isTrue();
    }

    @Test
    void 결제_뒤_주문_처리가_실패해도_결제는_유지된다() {
        stubTossSuccess();
        // 토스 승인을 기다리는 사이 주문이 먼저 취소되어 주문 확정이 실패하는 상황
        jdbcTemplate.update("UPDATE orders SET order_status = 'CANCELLED' WHERE id = ?", ORDER_ID);

        final Payment payment = paymentService.confirm(TOSS_ORDER_ID, PAYMENT_KEY, TOTAL_PRICE);
        awaitEventsHandled(jdbcTemplate);

        assertThat(payment.getStatus().name()).isEqualTo("DONE");
        assertThat(paymentCount()).isEqualTo(1);
        // 주문 처리 실패는 발행 기록에 미완료로 남아 재발행 대상이 된다.
        assertThat(incompletePublicationCount(jdbcTemplate, PaymentCompleted.class)).isEqualTo(1);
    }

    @Test
    void 금액이_일치하지_않으면_토스를_호출하지_않고_결제도_저장되지_않는다() {
        assertThatThrownBy(() -> paymentService.confirm(TOSS_ORDER_ID,PAYMENT_KEY, TOTAL_PRICE - 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(tossPaymentClient, never()).confirm(anyString(), anyString(), anyInt());
        assertThat(paymentCount()).isZero();
        assertThat(events.stream(PaymentCompleted.class).count()).isZero();
    }

    @Test
    void 존재하지_않는_주문이면_주문을_찾을_수_없다() {
        assertThatThrownBy(() -> paymentService.confirm("9999_test-token", PAYMENT_KEY, TOTAL_PRICE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);

        verify(tossPaymentClient, never()).confirm(anyString(), anyString(), anyInt());
        assertThat(paymentCount()).isZero();
    }

    @Test
    void 결제_실패로_복귀하면_결제를_저장하지_않고_PaymentFailed만_발행된다() {
        paymentService.fail(TOSS_ORDER_ID);

        assertThat(paymentCount()).isZero();
        assertThat(events.stream(PaymentFailed.class).map(PaymentFailed::orderId))
                .containsExactly(ORDER_ID);
        // 기본 픽스처 주문은 CONFIRMED이며 실패 복귀에도 변경되지 않는다.
        assertThat(orderStaysUntouched()).isTrue();
    }

    @Test
    void 결제_실패_후에도_원래_만료_시각까지_PENDING과_매물_선점을_유지한다() {
        markOrderPending(ORDER_ID);
        markListingPurchaseRequested(LISTING_ID);

        paymentService.fail(TOSS_ORDER_ID);
        awaitEventsHandled(jdbcTemplate);

        assertThat(paymentCount()).isZero();
        assertThat(orderExists(ORDER_ID)).isTrue();
        assertThat(orderStatus()).isEqualTo("PENDING");
        assertThat(listingPurchaseRequested(LISTING_ID)).isTrue();
        assertThat(events.stream(PaymentFailed.class).map(PaymentFailed::orderId))
                .containsExactly(ORDER_ID);

        jdbcTemplate.update("UPDATE orders SET pending_expires_at = DATE_SUB(NOW(6), INTERVAL 1 MINUTE) WHERE id = ?", ORDER_ID);
        assertThat(expirationService.expire(ORDER_ID, Instant.now())).isTrue();
        assertThat(orderStatus()).isEqualTo("EXPIRED");
        assertThat(listingPurchaseRequested(LISTING_ID)).isFalse();
    }

    @Test
    void 만료된_주문에_결제가_늦게_완료되면_결제와_EXPIRED_주문을_보존한다() {
        markOrderPending(ORDER_ID);
        jdbcTemplate.update("UPDATE orders SET pending_expires_at = DATE_SUB(NOW(6), INTERVAL 1 MINUTE) WHERE id = ?", ORDER_ID);
        assertThat(expirationService.expire(ORDER_ID, Instant.now())).isTrue();
        stubTossSuccess();

        final Payment payment = paymentService.confirm(TOSS_ORDER_ID, PAYMENT_KEY, TOTAL_PRICE);
        awaitEventsHandled(jdbcTemplate);

        assertThat(payment.getStatus().name()).isEqualTo("DONE");
        assertThat(orderStatus()).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM orders WHERE id = ?", String.class, ORDER_ID)).isEqualTo("PENDING");
        assertThat(incompletePublicationCount(jdbcTemplate, PaymentCompleted.class)).isZero();
    }

    private String orderStatus() {
        return jdbcTemplate.queryForObject("SELECT order_status FROM orders WHERE id = ?", String.class, ORDER_ID);
    }

    @Test
    void 승인_완료된_주문의_실패_복귀는_무시된다() {
        stubTossSuccess();
        paymentService.confirm(TOSS_ORDER_ID, PAYMENT_KEY, TOTAL_PRICE);

        paymentService.fail(TOSS_ORDER_ID);

        assertThat(paymentCount()).isEqualTo(1);
        assertThat(events.stream(PaymentFailed.class).count()).isZero();
        assertThat(orderStaysUntouched()).isTrue();
    }

    @Test
    void 실패한_주문을_재시도해_승인되면_DONE_1행이_저장된다() {
        paymentService.fail(TOSS_ORDER_ID);
        stubTossSuccess();

        final Payment retried = paymentService.confirm(TOSS_ORDER_ID, PAYMENT_KEY, TOTAL_PRICE);

        assertThat(retried.getStatus().name()).isEqualTo("DONE");
        assertThat(retried.getPaymentKey()).isEqualTo(PAYMENT_KEY);
        // 실패는 무기록이므로 재승인 시 DONE 1행이 새로 저장된다.
        assertThat(paymentCount()).isEqualTo(1);
        assertThat(events.stream(PaymentCompleted.class).count()).isEqualTo(1);
    }

    @Test
    void 구매자는_자신의_주문을_승인한다() {
        stubTossSuccess();

        final Payment payment = paymentService.confirm(BUYER_ID, TOSS_ORDER_ID, PAYMENT_KEY, TOTAL_PRICE);

        assertThat(payment.getStatus().name()).isEqualTo("DONE");
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void 다른_회원의_주문은_승인하지_않는다() {
        assertThatThrownBy(() -> paymentService.confirm(SELLER_ID, TOSS_ORDER_ID, PAYMENT_KEY, TOTAL_PRICE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);

        verify(tossPaymentClient, never()).confirm(anyString(), anyString(), anyInt());
        assertThat(paymentCount()).isZero();
    }

    @Test
    void 다른_회원의_주문은_실패_처리하지_않는다() {
        markOrderPending(ORDER_ID);

        assertThatThrownBy(() -> paymentService.fail(SELLER_ID, TOSS_ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        awaitEventsHandled(jdbcTemplate);

        assertThat(orderExists(ORDER_ID)).isTrue();
        assertThat(events.stream(PaymentFailed.class).count()).isZero();
    }

    @Test
    void 이미_승인된_결제를_다시_승인하면_재승인_없이_멱등_처리된다() {
        stubTossSuccess();
        paymentService.confirm(TOSS_ORDER_ID,PAYMENT_KEY, TOTAL_PRICE);

        final Payment again = paymentService.confirm(TOSS_ORDER_ID,PAYMENT_KEY, TOTAL_PRICE);

        assertThat(again.getStatus().name()).isEqualTo("DONE");
        assertThat(paymentCount()).isEqualTo(1);
        // 두 번째 호출은 토스를 다시 부르지 않는다(총 1회).
        verify(tossPaymentClient).confirm(eq(PAYMENT_KEY), eq(TOSS_ORDER_ID), eq(TOTAL_PRICE));
    }

    private void stubTossSuccess() {
        when(tossPaymentClient.confirm(eq(PAYMENT_KEY), eq(TOSS_ORDER_ID), eq(TOTAL_PRICE)))
                .thenReturn(new TossConfirmResult(
                        PAYMENT_KEY, TOSS_ORDER_ID, "DONE", TOTAL_PRICE, "2026-08-31T12:00:00+09:00"));
    }

    private boolean orderStaysUntouched() {
        final Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ? AND delivery_status = 'REQUESTED'",
                Integer.class, ORDER_ID);
        return count != null && count == 1;
    }

    private int paymentCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class);
    }

    private void markOrderPending(final long orderId) {
        jdbcTemplate.update("UPDATE orders SET delivery_status = 'PENDING', order_status = 'PENDING' WHERE id = ?", orderId);
    }

    private void markListingPurchaseRequested(final long listingId) {
        jdbcTemplate.update("UPDATE listings SET has_purchase_request = true WHERE id = ?", listingId);
    }

    private boolean orderExists(final long orderId) {
        final Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ?", Integer.class, orderId);
        return count != null && count == 1;
    }

    private boolean listingPurchaseRequested(final long listingId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT has_purchase_request FROM listings WHERE id = ?", Boolean.class, listingId));
    }

    private void insertMember(final long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?)
                """,
                memberId,
                "member" + memberId,
                "token-" + memberId
        );
    }

    private void insertListing(final long listingId, final long sellerId) {
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, deleted_at)
                VALUES (?, ?, '테스트 책상', '테스트 설명', ?, ?, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', false, NOW(6), NOW(6), NULL)
                """,
                listingId,
                sellerId,
                PRICE,
                DELIVERY_FEE
        );
    }

    private void insertPendingOrder(final long orderId, final long listingId, final long buyerId) {
        jdbcTemplate.update(
                "INSERT INTO orders (id, listing_id, buyer_id, delivery_status, order_status) VALUES (?, ?, ?, 'REQUESTED', 'CONFIRMED')",
                orderId, listingId, buyerId);
    }
}
