package setty.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import setty.global.exception.BusinessException;
import setty.platform.DeliverySupportReader;
import setty.platform.OrderSupportReader;
import setty.platform.OrderSupportReader.Search;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@Transactional
class OrderSupportQueryIntegrationTest extends MySqlIntegrationTestSupport {
    private static final long BUYER = 810001L;
    private static final long OTHER = 810002L;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderSupportReader reader;
    @Autowired setty.platform.OrderSupportCancellation cancellation;
    @MockitoBean DeliverySupportReader delivery;
    @MockitoBean setty.platform.listing.storage.ListingImageStorage images;

    @Test
    void cancelledSearchFindsOlderCancelledOrdersWithoutLeakingOtherBuyersAndKeepsPagination() {
        order(1, BUYER, "의자", null, "CANCELLED");
        order(2, BUYER, "의자", null, "CANCELLED");
        order(3, OTHER, "의자", null, "CANCELLED");
        for (int i=4; i<10; i++) order(i, BUYER, "의자", null, "CONFIRMED");
        var first = reader.findMyOrders(BUYER, Search.browse(null, null, null, null, null, 1).onlyCancelled());
        assertThat(first.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(830002L);
        var next = reader.findMyOrders(BUYER, Search.browse(null, null, null, null, first.nextCursor(), 1).onlyCancelled());
        assertThat(next.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(830001L);
        assertThat(next.nextCursor()).isNull();
        assertThat(reader.findMyOrders(BUYER, Search.browse(830003L, null, null, null, null, 5).onlyCancelled()).orders()).isEmpty();
    }

    @Test
    void orderCardsUseTheFirstListingPhoto() {
        order(1, BUYER, "사진 있는 소파", null, "CONFIRMED");
        jdbc.update("INSERT INTO listing_images(listing_id,object_key,display_order) VALUES (820001,'second',1),(820001,'first',0)");
        when(images.publicUrl("first")).thenReturn("https://example.test/first.jpg");
        assertThat(reader.findMyOrders(BUYER, Search.browse(null, null, null, null, null, 5)).orders())
                .singleElement().satisfies(candidate -> assertThat(candidate.thumbnailUrl()).isEqualTo("https://example.test/first.jpg"));
    }

    @BeforeEach
    void seed() {
        for (long id : new long[]{BUYER, OTHER}) {
            jdbc.update("INSERT INTO members(id,login_id,password,role,phone_number,address) "
                    + "VALUES (?,?,'encoded','MEMBER','010-0000-0000','synthetic')", id, "support-" + id);
        }
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void everyListAndCursorUsesDescendingIdsEvenWhenDatesDisagree(boolean cancellationTargets) {
        order(1, BUYER, "의자", "2026-11-05T00:00:00Z", "CONFIRMED");
        order(2, BUYER, "의자", "2026-11-03T00:00:00Z", "CONFIRMED");
        order(3, BUYER, "의자", null, "CONFIRMED");
        order(4, BUYER, "의자", "2026-11-01T00:00:00Z", "CONFIRMED");
        order(5, OTHER, "의자", null, "CONFIRMED");
        var ids = new java.util.ArrayList<Long>();
        Long cursor = null;
        do {
            var page = reader.findMyOrders(BUYER, (cancellationTargets ? Search.cancellationTargets(null, "의자", null, null, cursor, 2, null) : Search.browse(null, "의자", null, null, cursor, 2)));
            ids.addAll(page.orders().stream().map(OrderSupportReader.Candidate::orderId).toList());
            cursor = page.nextCursor();
        } while (cursor != null);
        assertThat(ids).containsExactly(830004L,830003L,830002L,830001L).doesNotHaveDuplicates();
        assertThat(reader.findMyOrders(BUYER,Search.latest("의자")).orders())
                .extracting(OrderSupportReader.Candidate::orderId).containsExactly(ids.getFirst());
    }

    @Test void dateSearchCountsOnlyMatchingOwnersUndatedOrders() {
        order(1,BUYER,"의자",null,"CONFIRMED");
        order(2,BUYER,"의자",null,"CONFIRMED");
        order(3,OTHER,"의자",null,"CONFIRMED");
        order(4,BUYER,"책상",null,"CONFIRMED");
        var found=reader.findMyOrders(BUYER,Search.browse(null, "의자", LocalDate.of(2026,11,3), null, null, 5));
        assertThat(tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(found).path("excludedUndatedCount").asLong())
                .isEqualTo(2);
    }

    @Test
    void kstDateBoundariesExcludeUndatedAndOtherBuyers() {
        order(1, BUYER, "소파", "2026-11-02T14:59:59Z", "CONFIRMED");
        order(2, BUYER, "소파", "2026-11-02T15:00:00Z", "CONFIRMED");
        order(3, BUYER, "소파", "2026-11-03T14:59:59.999999Z", "CONFIRMED");
        order(4, BUYER, "소파", "2026-11-03T15:00:00Z", "CONFIRMED");
        order(5, BUYER, "소파", null, "CONFIRMED");
        order(6, OTHER, "소파", "2026-11-03T01:00:00Z", "CONFIRMED");
        var result = reader.findMyOrders(BUYER, Search.browse(null, "소파", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), null, 5));
        assertThat(result.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(830003L, 830002L);
        assertThat(result.orders().getFirst().createdAt()).isEqualTo(Instant.parse("2026-11-03T14:59:59.999999Z"));
        assertThat(result.hasUndatedOrders()).isTrue();
        assertThat(reader.findMyOrders(BUYER, Search.browse(830005L, null, null, null, null, 5)).orders())
                .singleElement().satisfies(candidate -> assertThat(candidate.createdAt()).isNull());
    }

    @Test
    void paginationUsesIdAndLiteralTitleSearch() {
        for (int i = 1; i <= 7; i++) order(i, BUYER, "소파 50%_", null, "CONFIRMED");
        order(8, BUYER, "다른 가구", null, "CONFIRMED");
        var first = reader.findMyOrders(BUYER, Search.browse(null, "%_", null, null, null, 5));
        assertThat(first.orders()).hasSize(5);
        assertThat(first.nextCursor()).isEqualTo(830003L);
        var second = reader.findMyOrders(BUYER, Search.browse(null, "%_", null, null, first.nextCursor(), 5));
        assertThat(second.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(830002L, 830001L);
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void alternativeCancellationTargetsKeepOwnershipFilterAndIdCursor() {
        order(1,BUYER,"책상","2026-11-05T00:00:00Z","CONFIRMED");
        order(2,BUYER,"책상","2026-11-03T00:00:00Z","CANCEL_PENDING");
        order(3,BUYER,"책상","2026-11-04T00:00:00Z","CONFIRMED");
        order(4,BUYER,"책상","2026-11-06T00:00:00Z","CANCELLED");
        order(5,OTHER,"책상","2026-11-04T00:00:00Z","CONFIRMED");
        order(6,BUYER,"소파","2026-11-04T00:00:00Z","CONFIRMED");
        order(7,BUYER,"책상",null,"CONFIRMED");
        var first=reader.findMyOrders(BUYER,Search.cancellationTargets(null, "책상", null, null, null, 1, 830001L));
        assertThat(first.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(830007L);
        var second=reader.findMyOrders(BUYER,Search.cancellationTargets(null, "책상", null, null, first.nextCursor(), 1, 830001L));
        assertThat(second.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(830003L);
        var third=reader.findMyOrders(BUYER,Search.cancellationTargets(null, "책상", null, null, second.nextCursor(), 1, 830001L));
        assertThat(third.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(830002L);
        assertThat(third.nextCursor()).isNull();
        assertThat(reader.findMyOrders(BUYER,Search.cancellationTargets(null, "책상", null, null, 830005L, 5, 830001L)).orders()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"REQUESTED,ELIGIBLE,BEFORE_DRIVER_ACCEPTANCE", "ACCEPTED,INELIGIBLE,DRIVER_ACCEPTED",
            "PICKED_UP,INELIGIBLE,ALREADY_PICKED_UP", "DELIVERED,INELIGIBLE,ALREADY_DELIVERED",
            "CANCELLED,UNKNOWN,STATE_NOT_SYNCHRONIZED"})
    void usesActualDeliveryRatherThanOrderProjection(String state, String eligibility, String reason) {
        order(1, BUYER, "소파", null, "CONFIRMED");
        when(delivery.findState(830001L)).thenReturn(Optional.of(DeliverySupportReader.State.valueOf(state)));
        var before = jdbc.queryForMap("SELECT * FROM orders WHERE id=830001");
        var context = reader.getContext(BUYER, 830001L);
        assertThat(context.eligibility().name()).isEqualTo(eligibility);
        assertThat(context.reasonCode()).isEqualTo(reason);
        assertThat(context.observedAt()).isNotNull();
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=830001")).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"PENDING,PAYMENT_PENDING", "EXPIRED,ORDER_EXPIRED", "CANCEL_PENDING,CANCELLATION_PENDING",
            "CANCELLED,ALREADY_CANCELLED", "COMPLETED,PURCHASE_COMPLETED"})
    void terminalOrPendingOrderStatesAreNotEligible(String status, String reason) {
        order(1, BUYER, "소파", null, status);
        var result = reader.getContext(BUYER, 830001L);
        assertThat(result.eligibility()).isEqualTo(OrderSupportReader.Eligibility.INELIGIBLE);
        assertThat(result.reasonCode()).isEqualTo(reason);
    }

    @Test
    void paidOrderIsIneligibleWithoutCheckingDelivery() {
        order(1, BUYER, "소파", null, "PAID");

        var result = reader.getContext(BUYER, 830001L);

        assertThat(result.eligibility()).isEqualTo(OrderSupportReader.Eligibility.INELIGIBLE);
        assertThat(result.reasonCode()).isEqualTo("ORDER_CONFIRMATION_PENDING");
        verifyNoInteractions(delivery);
    }

    @Test
    void absenceAndFailureDifferAndOwnershipIsRechecked() {
        order(1, BUYER, "소파", null, "CONFIRMED");
        assertThat(reader.getContext(BUYER, 830001L).eligibility()).isEqualTo(OrderSupportReader.Eligibility.ELIGIBLE);
        when(delivery.findState(830001L)).thenThrow(new DataAccessResourceFailureException("synthetic"));
        var failed = reader.getContext(BUYER, 830001L);
        assertThat(failed.eligibility()).isEqualTo(OrderSupportReader.Eligibility.UNKNOWN);
        assertThat(failed.reasonCode()).isEqualTo("DELIVERY_LOOKUP_FAILED");
        assertThatThrownBy(() -> reader.getContext(OTHER, 830001L)).isInstanceOf(BusinessException.class);
        assertThat(reader.findMyOrders(OTHER, Search.browse(830001L, null, null, null, null, 5)).orders()).isEmpty();
    }

    @Test
    void withdrawnMemberIsNotActive() {
        assertThat(reader.isActiveMember(BUYER)).isTrue();
        assertThat(reader.lockActiveMember(BUYER)).isTrue();
        jdbc.update("UPDATE members SET withdrawn_at=CURRENT_TIMESTAMP WHERE id=?", BUYER);
        assertThat(reader.isActiveMember(BUYER)).isFalse();
        assertThat(reader.lockActiveMember(BUYER)).isFalse();
    }

    @Test void tenItemPageIncludesPriceAndDoesNotIncludeAnotherBuyer() {
        for(int i=1;i<=12;i++) order(i,BUYER,"의자",null,"CONFIRMED");
        order(13,OTHER,"의자",null,"CONFIRMED");
        jdbc.update("UPDATE orders SET delivery_fee=20000 WHERE id=830012");
        var first=reader.findMyOrders(BUYER,Search.browse(null, null, null, null, null, 10));
        assertThat(first.orders()).hasSize(10);
        assertThat(first.orders().getFirst().totalPrice()).isEqualTo(140000);
        assertThat(first.orders().getFirst().deliveryStatus()).isEqualTo("REQUESTED");
        assertThat(first.nextCursor()).isEqualTo(830003L);
        assertThat(reader.findMyOrders(BUYER,Search.browse(null, null, null, null, first.nextCursor(), 10)).orders()).hasSize(2);
        assertThatThrownBy(()->reader.findMyOrders(BUYER,Search.browse(null, null, null, null, null, 21))).isInstanceOf(BusinessException.class);
    }

    @Test
    void latestOrderIncludesUndatedAndNeverIncludesOtherBuyers() {
        order(1, BUYER, "최신 소파", "2026-11-04T15:00:00Z", "CONFIRMED");
        order(2, BUYER, "오래된 의자", "2026-11-03T15:00:00Z", "CONFIRMED");
        order(3, BUYER, "날짜 미상", null, "CONFIRMED");
        order(4, OTHER, "타인 최신 주문", "2026-11-05T15:00:00Z", "CONFIRMED");
        var latest=reader.findMyOrders(BUYER,Search.latest(null));
        assertThat(latest.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(830003L);
        assertThat(latest.hasUndatedOrders()).isFalse();
        assertThat(latest.nextCursor()).isNull();
    }

    @Test
    void cancellationTargetIsLatestMatchingNonCancelledOrderOfAuthenticatedBuyer() {
        order(1, BUYER, "책상", "2026-11-04T15:00:00Z", "CONFIRMED");
        order(2, BUYER, "책상", "2026-11-05T15:00:00Z", "CANCELLED");
        order(3, BUYER, "소파", "2026-11-06T15:00:00Z", "CONFIRMED");
        order(4, OTHER, "책상", "2026-11-07T15:00:00Z", "CONFIRMED");
        order(5, BUYER, "책상", null, "CONFIRMED");
        var found = reader.findMyOrders(BUYER, Search.latest("책상"));
        assertThat(found.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(830005L);
        assertThat(found.hasUndatedOrders()).isFalse();
        assertThat(reader.findMyOrders(BUYER,Search.browse(830002L, null, null, null, null, 5)).orders())
                .singleElement().satisfies(o -> assertThat(o.orderStatus()).isEqualTo("CANCELLED"));
    }

    @Test
    void latestCancellationTargetDoesNotSkipPendingOrIneligibleOrders() {
        order(1, BUYER, "책상", "2026-11-04T15:00:00Z", "CONFIRMED");
        order(2, BUYER, "책상", "2026-11-05T15:00:00Z", "CANCEL_PENDING");
        assertThat(reader.findMyOrders(BUYER,Search.latest("책상")).orders())
                .extracting(OrderSupportReader.Candidate::orderId).containsExactly(830002L);
    }

    @Test
    void latestOrderFindsNewestWhenOnlyUndatedOrdersExist() {
        order(1, BUYER, "날짜 미상", null, "CONFIRMED");
        var latest=reader.findMyOrders(BUYER,Search.latest(null));
        assertThat(latest.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(830001L);
        assertThat(latest.hasUndatedOrders()).isFalse();
    }

    @Test
    void confirmedConsentUsesExistingCancellationTransitionAndOwnership() {
        order(1, BUYER, "취소 테스트", null, "CONFIRMED");
        when(delivery.findState(830001L)).thenReturn(Optional.of(DeliverySupportReader.State.REQUESTED));
        var accepted=cancellation.request(BUYER,830001L);
        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.requestId()).isNotBlank();
        // Flush the real JPA transition before checking the same transactional JDBC database.
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id=830001",String.class)).isEqualTo("CANCEL_PENDING");
        assertThat(jdbc.queryForObject("SELECT cancellation_request_id FROM orders WHERE id=830001",String.class)).isEqualTo(accepted.requestId());
        assertThat(cancellation.request(BUYER,830001L).accepted()).isFalse();
    }

    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void consentCannotCancelAnotherBuyersOrder() {
        order(1, OTHER, "다른 구매자", null, "CONFIRMED");
        assertThatThrownBy(()->cancellation.request(BUYER,830001L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void driverAcceptanceBeforeConsentBlocksRealMutation() {
        order(1, BUYER, "변경된 상태", null, "CONFIRMED");
        when(delivery.findState(830001L)).thenReturn(Optional.of(DeliverySupportReader.State.ACCEPTED));
        var rejected=cancellation.request(BUYER,830001L);
        entityManager.flush();
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.context().reasonCode()).isEqualTo("DRIVER_ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id=830001",String.class)).isEqualTo("CONFIRMED");
    }

    private void order(int n, long buyer, String title, String createdAt, String status) {
        long listing = 820000L + n;
        jdbc.update("""
                INSERT INTO listings(id,seller_id,title,description,price,delivery_fee,category,condition_grade,
                    width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at)
                VALUES (?, ?, ?, 'synthetic',120000,10000,'CHAIR','A',10,10,10,'RESERVED',true,NOW(6),NOW(6))
                """, listing, OTHER, title);
        jdbc.update("INSERT INTO orders(id,listing_id,buyer_id,delivery_status,order_status,created_at) "
                        + "VALUES (?,?,?,'REQUESTED',?,FROM_UNIXTIME(?))",
                830000L+n, listing, buyer, status, createdAt == null ? null :
                        new java.math.BigDecimal(Instant.parse(createdAt).getEpochSecond())
                                .add(java.math.BigDecimal.valueOf(Instant.parse(createdAt).getNano(), 9)));
    }
}
