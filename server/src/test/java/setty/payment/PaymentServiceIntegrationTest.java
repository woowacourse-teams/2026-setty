package setty.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.common.PaymentCompleted;
import setty.common.PaymentFailed;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.application.PaymentService;
import setty.payment.domain.Payment;
import setty.payment.infrastructure.TossConfirmResult;
import setty.payment.infrastructure.TossPaymentClient;
import setty.platform.listing.storage.ListingImageStorage;

@SpringBootTest
@Testcontainers
@RecordApplicationEvents
class PaymentServiceIntegrationTest {

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

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.6")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    @Autowired
    private PaymentService paymentService;

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
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM delivery");
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
        // 기본 픽스처 주문은 REQUESTED — PENDING이 아닌 주문은 실패 복귀에도 삭제되지 않는다.
        assertThat(orderStaysUntouched()).isTrue();
    }

    @Test
    void PENDING_주문의_결제가_실패하면_주문이_삭제되고_매물_선점이_해제된다() {
        markOrderPending(ORDER_ID);
        markListingPurchaseRequested(LISTING_ID);

        paymentService.fail(TOSS_ORDER_ID);

        assertThat(paymentCount()).isZero();
        assertThat(orderExists(ORDER_ID)).isFalse();
        assertThat(listingPurchaseRequested(LISTING_ID)).isFalse();
        assertThat(events.stream(PaymentFailed.class).map(PaymentFailed::orderId))
                .containsExactly(ORDER_ID);
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
        jdbcTemplate.update("UPDATE orders SET delivery_status = 'PENDING' WHERE id = ?", orderId);
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
                "INSERT INTO orders (id, listing_id, buyer_id, delivery_status) VALUES (?, ?, ?, 'REQUESTED')",
                orderId, listingId, buyerId);
    }
}
