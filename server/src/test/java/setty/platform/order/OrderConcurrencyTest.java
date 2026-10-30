package setty.platform.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.member.domain.Member;
import setty.platform.member.repository.MemberRepository;
import setty.platform.member.service.MemberWithdrawalService;
import setty.platform.order.controller.dto.OrderCreateRequest;
import setty.platform.order.domain.Order;
import setty.platform.order.service.OrderService;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class OrderConcurrencyTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 101L;
    private static final long FIRST_BUYER_ID = 202L;
    private static final long SECOND_BUYER_ID = 303L;
    private static final long LISTING_ID = 11L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberWithdrawalService memberWithdrawalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(SELLER_ID);
        insertMember(FIRST_BUYER_ID);
        insertMember(SECOND_BUYER_ID);
        insertListing(LISTING_ID, SELLER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 같은_매물에_동시에_주문하면_한_건만_성공한다() throws InterruptedException {
        final Member firstBuyer = memberRepository.findById(FIRST_BUYER_ID).orElseThrow();
        final Member secondBuyer = memberRepository.findById(SECOND_BUYER_ID).orElseThrow();

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(2);
        final List<Object> results = new CopyOnWriteArrayList<>();

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        for (final Member buyer : List.of(firstBuyer, secondBuyer)) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    results.add(orderService.pending(new OrderCreateRequest(LISTING_ID), buyer));
                } catch (final Exception e) {
                    results.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        final boolean finished = done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();

        final List<Object> successes = results.stream()
                .filter(result -> !(result instanceof Exception))
                .toList();
        final List<Exception> failures = results.stream()
                .filter(Exception.class::isInstance)
                .map(Exception.class::cast)
                .toList();

        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_ORDERED);

        final Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE listing_id = ?", Integer.class, LISTING_ID);
        assertThat(orderCount).isEqualTo(1);
    }

    @Test
    void 같은_구매자가_동시에_주문하면_같은_결제_대기_주문_하나를_받는다() throws InterruptedException {
        final Member buyer = memberRepository.findById(FIRST_BUYER_ID).orElseThrow();

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(2);
        final List<Object> results = new CopyOnWriteArrayList<>();

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        for (int attempt = 0; attempt < 2; attempt++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    results.add(orderService.pending(new OrderCreateRequest(LISTING_ID), buyer));
                } catch (final Exception e) {
                    results.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        final boolean finished = done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(results).hasSize(2).allSatisfy(result -> assertThat(result).isInstanceOf(Order.class));
        assertThat(results.stream().map(result -> ((Order) result).getId()).distinct()).hasSize(1);

        final Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE listing_id = ?", Integer.class, LISTING_ID);
        assertThat(orderCount).isEqualTo(1);
    }

    @Test
    void 판매자_탈퇴와_그_매물_주문이_동시에_오면_한쪽만_성공한다() throws InterruptedException {
        final Member buyer = memberRepository.findById(FIRST_BUYER_ID).orElseThrow();

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(2);
        final List<Object> results = new CopyOnWriteArrayList<>();
        final List<Runnable> tasks = List.of(
                () -> results.add(orderService.pending(new OrderCreateRequest(LISTING_ID), buyer)),
                () -> {
                    memberWithdrawalService.withdraw(SELLER_ID);
                    results.add("withdrawn");
                }
        );

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        for (final Runnable task : tasks) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                } catch (final Exception e) {
                    results.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        final boolean finished = done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(results).hasSize(2);
        assertThat(results.stream().filter(Exception.class::isInstance)).hasSize(1);

        final Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE listing_id = ?", Integer.class, LISTING_ID);
        final boolean withdrawn = memberRepository.findById(SELLER_ID).orElseThrow().isWithdrawn();
        if (withdrawn) {
            assertThat(orderCount).isZero();
            assertThat(errorCodeOf(results)).isEqualTo(ErrorCode.LISTING_NOT_FOUND);
        } else {
            assertThat(orderCount).isEqualTo(1);
            assertThat(errorCodeOf(results)).isEqualTo(ErrorCode.MEMBER_HAS_ACTIVE_TRADE);
        }
    }

    @Test
    void 구매자_탈퇴와_그_구매자의_주문이_동시에_오면_한쪽만_성공한다() throws InterruptedException {
        final Member buyer = memberRepository.findById(FIRST_BUYER_ID).orElseThrow();

        final List<Object> results = runTogether(
                () -> orderService.pending(new OrderCreateRequest(LISTING_ID), buyer),
                () -> {
                    memberWithdrawalService.withdraw(FIRST_BUYER_ID);
                    return "withdrawn";
                }
        );

        assertThat(results.stream().filter(Exception.class::isInstance)).hasSize(1);
        final Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE buyer_id = ?", Integer.class, FIRST_BUYER_ID);
        final boolean withdrawn = memberRepository.findById(FIRST_BUYER_ID).orElseThrow().isWithdrawn();
        if (withdrawn) {
            assertThat(orderCount).isZero();
            assertThat(errorCodeOf(results)).isEqualTo(ErrorCode.INVALID_TOKEN);
        } else {
            assertThat(orderCount).isEqualTo(1);
            assertThat(errorCodeOf(results)).isEqualTo(ErrorCode.MEMBER_HAS_ACTIVE_TRADE);
        }
    }

    @Test
    void 탈퇴한_구매자는_주문할_수_없다() {
        final Member staleBuyer = memberRepository.findById(FIRST_BUYER_ID).orElseThrow();
        memberWithdrawalService.withdraw(FIRST_BUYER_ID);

        assertThatThrownBy(() -> orderService.pending(new OrderCreateRequest(LISTING_ID), staleBuyer))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    private List<Object> runTogether(final java.util.concurrent.Callable<Object> first,
                                     final java.util.concurrent.Callable<Object> second) throws InterruptedException {
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(2);
        final List<Object> results = new CopyOnWriteArrayList<>();
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        for (final java.util.concurrent.Callable<Object> task : List.of(first, second)) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    results.add(task.call());
                } catch (final Exception e) {
                    results.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();
        assertThat(results).hasSize(2);
        return results;
    }

    private ErrorCode errorCodeOf(final List<Object> results) {
        return results.stream()
                .filter(BusinessException.class::isInstance)
                .map(result -> ((BusinessException) result).getErrorCode())
                .findFirst()
                .orElseThrow();
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
