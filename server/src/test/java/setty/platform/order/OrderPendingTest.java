package setty.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.domain.Order;
import setty.platform.order.service.OrderService;

@SpringBootTest
@Testcontainers
class OrderPendingTest {

    private static final long SELLER_ID = 101L;
    private static final long BUYER_ID = 202L;
    private static final long LISTING_ID = 11L;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.6")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    @Autowired
    private OrderService orderService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        jdbcTemplate.update("DELETE FROM favorites");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 결제_대기_주문이_생성되고_배차_요청은_발행되지_않는다() {
        final Member buyer = memberRepository.findById(BUYER_ID).orElseThrow();

        final Order order = orderService.pending(new OrderCreateRequest(LISTING_ID), buyer);

        final String status = jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM orders WHERE id = ?", String.class, order.getId());
        assertThat(status).isEqualTo("PENDING");

        final Integer deliveryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM delivery", Integer.class);
        assertThat(deliveryCount).isEqualTo(0);
    }

    @Test
    void 이미_주문된_매물의_결제_대기_주문은_거부된다() {
        final Member buyer = memberRepository.findById(BUYER_ID).orElseThrow();
        orderService.pending(new OrderCreateRequest(LISTING_ID), buyer);

        assertThatThrownBy(() -> orderService.pending(new OrderCreateRequest(LISTING_ID), buyer))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_ORDERED);
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
                VALUES (?, ?, '테스트 책상', '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', false, NOW(6), NOW(6), NULL)
                """,
                listingId,
                sellerId
        );
    }
}
