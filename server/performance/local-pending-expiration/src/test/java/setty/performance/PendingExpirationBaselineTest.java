package setty.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import setty.platform.order.domain.OrderStatus;
import setty.platform.order.repository.OrderRepository;
import setty.platform.order.service.PendingOrderExpirationScheduler;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

/** Real scheduler, transaction boundaries, row locks and MySQL; no production logic is replaced. */
@Tag("local-pending-expiration")
@SpringBootTest(properties = {
        "management.otlp.metrics.export.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "setty.performance.PendingExpirationBaselineTest$SqlCapture"
})
public class PendingExpirationBaselineTest extends MySqlIntegrationTestSupport {
    private static final long SELLER_ID = 910001;
    private static final long BUYER_ID = 910002;
    private static final int FUTURE_PENDING = 1_000;
    private static final int CLOSED_HISTORY = 1_000;
    private static final Instant REFERENCE = Instant.parse("2026-10-12T06:00:00Z");
    private static volatile CountDownLatch firstOrderLockQuery;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PendingOrderExpirationScheduler scheduler;
    @Autowired private OrderRepository orders;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private Clock clock;

    private final Path output = Path.of(System.getProperty("expiration.benchmark.output")).toAbsolutePath();
    private final List<Map<String, Object>> samples = new ArrayList<>();

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void measureCurrentSchedulerAndIsolateOneLockedOldestOrder() throws Exception {
        Files.createDirectories(output);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("setty_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings", Integer.class)).isZero();
        when(clock.instant()).thenReturn(REFERENCE);
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        try {
            seed(30);
            scheduler.expirePendingOrders();
            verify(30);
            for (int round = 1; round <= 3; round++) {
                var sizes = round % 2 == 0 ? List.of(1_000, 300, 30) : List.of(30, 300, 1_000);
                for (int size : sizes) measure(size, round);
            }
            measureLockedHead();
        } finally {
            Files.writeString(output.resolve("samples.json"), mapper.writeValueAsString(samples));
            cleanup();
        }
        Files.writeString(output.resolve("metadata.json"), mapper.writeValueAsString(Map.of(
                "reference_time", REFERENCE.toString(),
                "java", System.getProperty("java.version"),
                "mysql", jdbc.queryForMap("SELECT VERSION() version, @@innodb_lock_wait_timeout lock_wait_seconds"),
                "hikari_maximum_pool_size", dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize(),
                "fixed_background_future_pending", FUTURE_PENDING,
                "fixed_background_closed_history", CLOSED_HISTORY,
                "method", "Direct invocation of the unchanged Spring scheduler; HTTP and the 60s scheduling wait are excluded",
                "traffic_basis", "Exploratory synthetic cohort sizes, not observed or forecast user traffic")));
        System.out.println("PENDING_EXPIRATION_BASELINE complete; output=" + output);
    }

    private void measure(int size, int round) throws Exception {
        seed(size);
        SqlCapture.count.set(0);
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var maxActive = new AtomicInteger();
        var maxWaiting = new AtomicInteger();
        var sampling = new AtomicBoolean(true);
        var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        try (var executor = Executors.newSingleThreadScheduledExecutor()) {
            executor.scheduleAtFixedRate(() -> {
                if (sampling.get()) {
                    maxActive.accumulateAndGet(pool.getActiveConnections(), Math::max);
                    maxWaiting.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max);
                }
            }, 0, 10, TimeUnit.MILLISECONDS);
            long start = System.nanoTime();
            scheduler.expirePendingOrders();
            double elapsed = elapsedMs(start);
            sampling.set(false);
            var row = new LinkedHashMap<String, Object>();
            row.put("scenario", "uncontended");
            row.put("round", round);
            row.put("due", size);
            row.put("job_ms", elapsed);
            row.put("orders_per_second", size * 1_000.0 / elapsed);
            row.put("hibernate_sql", SqlCapture.count.get());
            row.put("hibernate_transactions", stats.getTransactionCount());
            row.put("sampled_max_active_connections", maxActive.get());
            row.put("sampled_max_waiting_connections", maxWaiting.get());
            samples.add(row);
            verify(size);
            System.out.println("PENDING_EXPIRATION_SAMPLE " + mapper.writeValueAsString(row));
        }
    }

    private void measureLockedHead() throws Exception {
        seed(30);
        firstOrderLockQuery = new CountDownLatch(1);
        var row = new LinkedHashMap<String, Object>();
        row.put("scenario", "oldest_order_locked");
        row.put("due", 30);
        try (Connection lock = dataSource.getConnection(); var executor = Executors.newSingleThreadExecutor()) {
            lock.setAutoCommit(false);
            try (var statement = lock.prepareStatement("SELECT id FROM orders WHERE id=1 FOR UPDATE")) {
                try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); }
            }
            long start = System.nanoTime();
            var task = executor.submit(scheduler::expirePendingOrders);
            try {
                assertThat(firstOrderLockQuery.await(5, TimeUnit.SECONDS)).isTrue();
                // A controlled DB lock fault, independent of natural traffic measurements.
                Thread.sleep(3_000);
                row.put("observed_lock_hold_ms", elapsedMs(start));
                row.put("other_orders_expired_while_locked", jdbc.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE id BETWEEN 2 AND 30 AND order_status='EXPIRED'", Integer.class));
                row.put("other_listings_released_while_locked", jdbc.queryForObject(
                        "SELECT COUNT(*) FROM listings WHERE id BETWEEN 2 AND 30 AND has_purchase_request=FALSE", Integer.class));
                assertThat(task.isDone()).isFalse();
                assertThat(row.get("other_orders_expired_while_locked")).isEqualTo(0);
                assertThat(row.get("other_listings_released_while_locked")).isEqualTo(0);
            } finally {
                lock.rollback();
                firstOrderLockQuery = null;
            }
            task.get(30, TimeUnit.SECONDS);
            row.put("job_ms", elapsedMs(start));
            samples.add(row);
            verify(30);
            System.out.println("PENDING_EXPIRATION_SAMPLE " + mapper.writeValueAsString(row));
        }
    }

    private void seed(int due) {
        cleanup();
        jdbc.update("""
                INSERT INTO members(id,login_id,password,role,phone_number,address)
                VALUES (?, 'expiry-seller', REPEAT('!',60), 'MEMBER', '000-0000-0000', 'LOCAL EXPERIMENT'),
                       (?, 'expiry-buyer', REPEAT('!',60), 'MEMBER', '000-0000-0000', 'LOCAL EXPERIMENT')
                """, SELLER_ID, BUYER_ID);
        List<Object[]> listings = new ArrayList<>();
        List<Object[]> orderRows = new ArrayList<>();
        for (int id = 1; id <= due + FUTURE_PENDING + CLOSED_HISTORY; id++) {
            boolean closed = id > due + FUTURE_PENDING;
            listings.add(new Object[]{id, SELLER_ID, !closed});
            String status = closed ? "EXPIRED" : "PENDING";
            Instant deadline = id <= due ? REFERENCE.minusSeconds(id == 1 ? 60 : 1)
                    : closed ? REFERENCE.minusSeconds(120) : REFERENCE.plusSeconds(600);
            // Hibernate binds Instant as UTC. Supply the same UTC SQL literal, independent of the host zone.
            orderRows.add(new Object[]{id, id, BUYER_ID, status,
                    deadline.toString().replace('T', ' ').replace("Z", "")});
        }
        jdbc.batchUpdate("""
                INSERT INTO listings(id,seller_id,title,description,price,delivery_fee,category,condition_grade,
                    width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at)
                VALUES (?,?,'[LOCAL] Expiration fixture','Synthetic data',50000,10000,'DESK','A',
                    80,50,70,'AVAILABLE',?,'2026-10-12 05:00:00','2026-10-12 05:00:00')
                """, listings);
        jdbc.batchUpdate("""
                INSERT INTO orders(id,listing_id,buyer_id,delivery_status,order_status,pending_expires_at)
                VALUES (?,?,?,'PENDING',?,?)
                """, orderRows);
        assertThat(orders.findExpiredOrderIds(OrderStatus.PENDING, REFERENCE)).hasSize(due).first().isEqualTo(1L);
    }

    private void verify(int due) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id<=? AND order_status='EXPIRED'", Integer.class, due))
                .isEqualTo(due);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE id<=? AND has_purchase_request=FALSE", Integer.class, due))
                .isEqualTo(due);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id>? AND order_status='PENDING'", Integer.class, due))
                .isEqualTo(FUTURE_PENDING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE id>? AND id<=? AND has_purchase_request=TRUE",
                Integer.class, due, due + FUTURE_PENDING)).isEqualTo(FUTURE_PENDING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isEqualTo(due + FUTURE_PENDING + CLOSED_HISTORY);
    }

    private void cleanup() {
        jdbc.update("DELETE FROM orders WHERE buyer_id=?", BUYER_ID);
        jdbc.update("DELETE FROM listings WHERE seller_id=?", SELLER_ID);
        jdbc.update("DELETE FROM members WHERE id IN (?,?)", SELLER_ID, BUYER_ID);
    }

    private static double elapsedMs(long start) { return (System.nanoTime() - start) / 1_000_000.0; }

    public static class SqlCapture implements StatementInspector {
        static final AtomicLong count = new AtomicLong();
        @Override public String inspect(String sql) {
            count.incrementAndGet();
            var latch = firstOrderLockQuery;
            if (latch != null && sql.contains("from orders") && sql.contains("for update")) latch.countDown();
            return sql;
        }
    }
}
