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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.payment.application.PaymentService;
import setty.payment.domain.Payment;
import setty.payment.infrastructure.TossConfirmResult;
import setty.payment.infrastructure.TossPaymentClient;
import setty.payment.presentation.dto.PaymentConfirmRequest;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;

@SpringBootTest
@Testcontainers
class PaymentServiceIntegrationTest {

    private static final long SELLER_ID = 101L;
    private static final long BUYER_ID = 202L;
    private static final long LISTING_ID = 11L;
    private static final int PRICE = 150_000;
    private static final int DELIVERY_FEE = 10_000;
    private static final int TOTAL_PRICE = PRICE + DELIVERY_FEE;
    private static final String TOSS_ORDER_ID = "toss-order-uuid-1";
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
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void 결제_승인에_성공하면_주문과_결제가_생성되고_배차_요청까지_이어진다() {
        when(tossPaymentClient.confirm(eq(PAYMENT_KEY), eq(TOSS_ORDER_ID), eq(TOTAL_PRICE)))
                .thenReturn(new TossConfirmResult(
                        PAYMENT_KEY, TOSS_ORDER_ID, "DONE", TOTAL_PRICE, "2026-08-31T12:00:00+09:00"));

        final Payment payment = paymentService.confirm(confirmRequest(TOTAL_PRICE), buyer());

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getAmount()).isEqualTo(TOTAL_PRICE);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(paymentCount()).isEqualTo(1);
        // OrderRequested 발행 → 배송 팀이 수신해 배차 요청 row를 만든다 (결제 후에만 발행됨).
        assertThat(deliveryCount()).isEqualTo(1);
    }

    @Test
    void 금액이_일치하지_않으면_토스를_호출하지_않고_주문도_생기지_않는다() {
        assertThatThrownBy(() -> paymentService.confirm(confirmRequest(TOTAL_PRICE - 1), buyer()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verify(tossPaymentClient, never()).confirm(anyString(), anyString(), anyInt());
        assertThat(orderCount()).isZero();
        assertThat(paymentCount()).isZero();
        assertThat(deliveryCount()).isZero();
    }

    @Test
    void 본인_매물은_결제할_수_없다() {
        final Member seller = memberRepository.findById(SELLER_ID).orElseThrow();

        assertThatThrownBy(() -> paymentService.confirm(confirmRequest(TOTAL_PRICE), seller))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_ORDER_OWN_LISTING);

        verify(tossPaymentClient, never()).confirm(anyString(), anyString(), anyInt());
        assertThat(orderCount()).isZero();
    }

    @Test
    void 이미_주문된_매물은_결제할_수_없다() {
        jdbcTemplate.update(
                "INSERT INTO orders (listing_id, buyer_id, delivery_status) VALUES (?, ?, 'REQUESTED')",
                LISTING_ID, BUYER_ID);

        assertThatThrownBy(() -> paymentService.confirm(confirmRequest(TOTAL_PRICE), buyer()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_ORDERED);

        verify(tossPaymentClient, never()).confirm(anyString(), anyString(), anyInt());
        assertThat(paymentCount()).isZero();
    }

    private Member buyer() {
        return memberRepository.findById(BUYER_ID).orElseThrow();
    }

    private PaymentConfirmRequest confirmRequest(final int amount) {
        return new PaymentConfirmRequest(LISTING_ID, TOSS_ORDER_ID, PAYMENT_KEY, amount);
    }

    private int orderCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
    }

    private int paymentCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class);
    }

    private int deliveryCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM delivery", Integer.class);
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
}
