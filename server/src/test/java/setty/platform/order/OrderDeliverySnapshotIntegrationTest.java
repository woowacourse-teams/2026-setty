package setty.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import setty.common.PaymentCompleted;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.member.repository.MemberRepository;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.service.OrderService;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class OrderDeliverySnapshotIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 7101L;
    private static final long BUYER_ID = 7202L;
    private static final long LISTING_ID = 7011L;
    private static final String SELLER_DONG = "1111054000";
    private static final String BUYER_DONG = "1111053000";

    @Autowired
    private OrderService orderService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM settlements");
        jdbcTemplate.update("DELETE FROM settlement_order");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 주문_뒤_회원_주소가_바뀌어도_배송_요청은_주문_시점_값으로_만든다() {
        insertMember(SELLER_ID, "판매자 주소", "010-0000-0001", SELLER_DONG, "MATCHED", "03045", "SELLER-MGMT");
        insertMember(BUYER_ID, "구매자 주소", "010-0000-0002", BUYER_DONG, "MATCHED", "03174", "BUYER-MGMT");
        insertListing(SELLER_DONG);

        final long orderId = order();
        jdbcTemplate.update("""
                UPDATE members SET address = '이사한 주소', phone_number = '010-9999-9999', dong_code = '1168064000',
                       zip_code = '06236', address_mgmt_no = 'MOVED-MGMT'
                """);
        completePayment(orderId);

        assertThat(jdbcTemplate.queryForMap("""
                SELECT pickup_address, pickup_phone_number, pickup_dong_code, pickup_zip_code,
                       delivery_address, delivery_phone_number, delivery_dong_code, delivery_zip_code
                FROM delivery WHERE order_id = ?
                """, orderId))
                .containsEntry("pickup_address", "판매자 주소")
                .containsEntry("pickup_phone_number", "010-0000-0001")
                .containsEntry("pickup_dong_code", SELLER_DONG)
                // 매물 구역(listings.zip_zone)이 없는 매물이라 출발지 우편번호가 없다
                .containsEntry("pickup_zip_code", null)
                .containsEntry("delivery_address", "구매자 주소")
                .containsEntry("delivery_phone_number", "010-0000-0002")
                .containsEntry("delivery_dong_code", BUYER_DONG)
                .containsEntry("delivery_zip_code", "03174");
        assertThat(jdbcTemplate.queryForMap("""
                SELECT pickup_address_mgmt_no, delivery_address_mgmt_no FROM orders WHERE id = ?
                """, orderId))
                .containsEntry("pickup_address_mgmt_no", "SELLER-MGMT")
                .containsEntry("delivery_address_mgmt_no", "BUYER-MGMT");
    }

    @Test
    void 표준_주소가_없는_회원도_주문하고_배송_요청을_받는다() {
        insertMember(SELLER_ID, "판매자 주소", "010-0000-0001", SELLER_DONG, "UNMATCHED", null, null);
        insertMember(BUYER_ID, "구매자 주소", "010-0000-0002", BUYER_DONG, null, null, null);
        insertListing(SELLER_DONG);

        final long orderId = order();
        completePayment(orderId);

        final Map<String, Object> delivery = jdbcTemplate.queryForMap("""
                SELECT status, pickup_dong_code, pickup_zip_code, delivery_dong_code, delivery_zip_code
                FROM delivery WHERE order_id = ?
                """, orderId);
        assertThat(delivery)
                .containsEntry("status", "REQUESTED")
                .containsEntry("pickup_dong_code", SELLER_DONG)
                .containsEntry("pickup_zip_code", null)
                .containsEntry("delivery_dong_code", BUYER_DONG)
                .containsEntry("delivery_zip_code", null);
    }

    private long order() {
        return orderService.pending(new OrderCreateRequest(LISTING_ID), memberRepository.findById(BUYER_ID).orElseThrow())
                .getId();
    }

    private void completePayment(final long orderId) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(new PaymentCompleted(orderId)));
        awaitEventsHandled(jdbcTemplate);
    }

    private void insertMember(
            final long id,
            final String address,
            final String phoneNumber,
            final String dongCode,
            final String addressStatus,
            final String zipCode,
            final String managementNumber
    ) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, dong_code,
                                     address_status, zip_code, address_mgmt_no)
                VALUES (?, ?, 'encoded-password', 'MEMBER', ?, ?, ?, ?, ?, ?)
                """, id, "member" + id, phoneNumber, address, dongCode, addressStatus, zipCode, managementNumber);
    }

    private void insertListing(final String dongCode) {
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category,
                                      condition_grade, width_cm, depth_cm, height_cm, sale_status,
                                      has_purchase_request, created_at, updated_at, dong_code)
                VALUES (?, ?, '테스트 책상', '테스트 설명', 150000, 10000, 'DESK',
                        'A', 60, 60, 70, 'AVAILABLE', false, NOW(6), NOW(6), ?)
                """, LISTING_ID, SELLER_ID, dongCode);
    }
}
