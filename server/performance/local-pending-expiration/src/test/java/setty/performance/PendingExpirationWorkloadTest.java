package setty.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

/** Measures the agreed 500-order cohort, using actual expiration transactions in temporary MySQL. */
@Tag("local-pending-expiration")
@SpringBootTest(properties = {
        "management.otlp.metrics.export.enabled=false",
        "setty.events.resubmission-interval=PT24H",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "setty.performance.PendingExpirationWorkloadTest$SqlCapture"
})
public class PendingExpirationWorkloadTest extends MySqlIntegrationTestSupport {
    private static final long SELLER = 930001;
    private static final long BUYER = 930002;
    private static final int INVENTORY = 5_000;
    private static final int COHORT = 500;
    private static final Instant START = Instant.parse("2026-10-12T09:00:00Z");
    private final AtomicReference<Instant> reference = new AtomicReference<>(START);
    private final List<Map<String, Object>> samples = new ArrayList<>();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private OrderRepository orders;
    @Autowired private PendingOrderExpirationScheduler scheduler;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private Clock clock;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void measureBurstAndTimeDistributedExpiration() throws Exception {
        Path output = Path.of(System.getProperty("expiration.benchmark.output")).toAbsolutePath();
        Files.createDirectories(output);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("setty_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings", Integer.class)).isZero();
        when(clock.instant()).thenAnswer(invocation -> reference.get());
        try {
            seedInventory();
            seedCohort(300, false);
            scheduler.expirePendingOrders();
            verify(300, 0, 200);
            for (int round = 1; round <= 3; round++) {
                var sizes = round % 2 == 0 ? List.of(500, 300) : List.of(300, 500);
                for (int due : sizes) {
                    seedCohort(due, false);
                    measure("burst", round, 1, due, 0, COHORT - due);
                }
            }
            for (int round = 1; round <= 3; round++) {
                seedCohort(300, true);
                for (int tick = 0; tick < 6; tick++) {
                    reference.set(START.plusSeconds(tick * 60L));
                    // Controlled clock replay: the next 50 become due; future orders must remain reserved.
                    measure("distributed_clock_replay", round, tick + 1,
                            50 * (tick + 1), 300 - 50 * (tick + 1), 200);
                }
            }
            Files.writeString(output.resolve("metadata.json"), mapper.writeValueAsString(Map.of(
                    "mysql", jdbc.queryForObject("SELECT VERSION()", String.class),
                    "java", System.getProperty("java.version"),
                    "inventory", INVENTORY, "order_cohort", COHORT,
                    "repetitions", 3,
                    "reference_start", START.toString(),
                    "method", "Direct unchanged scheduler calls, real Spring transactions and temporary MySQL; controlled clock replay for distributed cohorts",
                    "scope", "Expiration component only; creation/confirmation are seeded states. No HTTP, external payment, real six-minute wait or real scheduler cadence",
                    "pool_sampling_interval_ms", 10,
                    "result", "All cohort, listing reservation, confirmed-order and future-order assertions passed")));
        } finally {
            Files.writeString(output.resolve("samples.json"), mapper.writeValueAsString(samples));
            cleanup();
        }
    }

    private void measure(String scenario, int round, int tick, int expectedExpired,
                         int expectedPending, int expectedConfirmed) throws Exception {
        long lookupStart = System.nanoTime();
        var candidates = orders.findExpiredOrderIds(OrderStatus.PENDING, reference.get());
        double lookupMs = millis(lookupStart);
        int expectedDue = scenario.equals("burst") ? expectedExpired : 50;
        assertThat(candidates).hasSize(expectedDue);
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        SqlCapture.counts.set(new TreeMap<>());
        var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        var maxActive = new AtomicInteger();
        var maxWaiting = new AtomicInteger();
        double jobMs;
        try (var sampler = Executors.newSingleThreadScheduledExecutor()) {
            sampler.scheduleAtFixedRate(() -> {
                maxActive.accumulateAndGet(pool.getActiveConnections(), Math::max);
                maxWaiting.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max);
            }, 0, 10, TimeUnit.MILLISECONDS);
            long start = System.nanoTime();
            scheduler.expirePendingOrders();
            jobMs = millis(start);
        }
        var sql = new TreeMap<>(SqlCapture.counts.get());
        SqlCapture.counts.remove();
        var sample = new LinkedHashMap<String, Object>();
        sample.put("scenario", scenario);
        sample.put("round", round);
        sample.put("tick", tick);
        sample.put("due_this_scan", candidates.size());
        sample.put("reference_time", reference.get().toString());
        sample.put("candidate_lookup_probe_ms", lookupMs);
        sample.put("job_ms", jobMs);
        sample.put("hibernate_sql_by_kind", sql);
        sample.put("hibernate_sql", sql.values().stream().mapToLong(Long::longValue).sum());
        sample.put("hibernate_transactions", stats.getTransactionCount());
        sample.put("sampled_max_active_connections", maxActive.get());
        sample.put("sampled_max_waiting_connections", maxWaiting.get());
        sample.put("remaining_due", orders.findExpiredOrderIds(OrderStatus.PENDING, reference.get()).size());
        sample.put("expired_total", expectedExpired);
        sample.put("future_pending_preserved", expectedPending);
        sample.put("confirmed_preserved", expectedConfirmed);
        samples.add(sample);
        verify(expectedExpired, expectedPending, expectedConfirmed);
        assertThat(sample.get("remaining_due")).isEqualTo(0);
        System.out.println("PENDING_EXPIRATION_WORKLOAD " + mapper.writeValueAsString(sample));
    }

    private void seedInventory() {
        jdbc.update("""
                INSERT INTO members(id,login_id,password,role,phone_number,address)
                VALUES (?, 'workload-seller', REPEAT('!',60), 'MEMBER', '000-0000-0000', 'LOCAL EXPERIMENT'),
                       (?, 'workload-buyer', REPEAT('!',60), 'MEMBER', '000-0000-0000', 'LOCAL EXPERIMENT')
                """, SELLER, BUYER);
        // Chunked multi-row fixture inserts avoid measuring thousands of fixture round trips.
        for (int base = 1; base <= INVENTORY; base += 100) {
            var args = new ArrayList<Object>();
            var rows = new ArrayList<String>();
            for (int id = base; id < base + 100 && id <= INVENTORY; id++) {
                rows.add("(?,?,'[LOCAL] Workload fixture','Synthetic data',50000,10000,'DESK','A',80,50,70,'AVAILABLE',FALSE,'2026-10-12 08:00:00','2026-10-12 08:00:00')");
                args.add(id);
                args.add(SELLER);
            }
            jdbc.update("INSERT INTO listings(id,seller_id,title,description,price,delivery_fee,category,condition_grade,width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at) VALUES "
                    + String.join(",", rows), args.toArray());
        }
    }

    private void seedCohort(int unpaid, boolean distributed) {
        jdbc.update("DELETE FROM orders WHERE buyer_id=?", BUYER);
        jdbc.update("UPDATE listings SET has_purchase_request=TRUE, sale_status='AVAILABLE' WHERE seller_id=? AND id<=?", SELLER, COHORT);
        if (unpaid < COHORT) {
            jdbc.update("UPDATE listings SET sale_status='RESERVED' WHERE seller_id=? AND id>? AND id<=?", SELLER, unpaid, COHORT);
        }
        reference.set(START);
        var rows = new ArrayList<Object[]>();
        for (int id = 1; id <= COHORT; id++) {
            boolean pending = id <= unpaid;
            Instant deadline = distributed && pending ? START.plusSeconds(((id - 1) / 50) * 60L) : START.minusSeconds(1);
            rows.add(new Object[]{id, id, BUYER, pending ? "PENDING" : "REQUESTED", pending ? "PENDING" : "CONFIRMED",
                    deadline.toString().replace('T', ' ').replace("Z", "")});
        }
        jdbc.batchUpdate("INSERT INTO orders(id,listing_id,buyer_id,delivery_status,order_status,pending_expires_at) VALUES (?,?,?,?,?,?)", rows);
    }

    private void verify(int expired, int pending, int confirmed) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id=?", Integer.class, BUYER)).isEqualTo(COHORT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE seller_id=?", Integer.class, SELLER)).isEqualTo(INVENTORY);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id=? AND order_status='EXPIRED'", Integer.class, BUYER)).isEqualTo(expired);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id=? AND order_status='PENDING'", Integer.class, BUYER)).isEqualTo(pending);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id=? AND order_status='CONFIRMED'", Integer.class, BUYER)).isEqualTo(confirmed);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders o JOIN listings l ON l.id=o.listing_id WHERE o.buyer_id=?
                  AND ((o.order_status='EXPIRED' AND (l.has_purchase_request=TRUE OR l.sale_status<>'AVAILABLE'))
                    OR (o.order_status='PENDING' AND (l.has_purchase_request=FALSE OR l.sale_status<>'AVAILABLE'))
                    OR (o.order_status='CONFIRMED' AND (l.has_purchase_request=FALSE OR l.sale_status<>'RESERVED')))
                """, Integer.class, BUYER)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE seller_id=? AND id>? AND has_purchase_request=FALSE AND sale_status='AVAILABLE'",
                Integer.class, SELLER, COHORT)).isEqualTo(INVENTORY - COHORT);
    }

    private void cleanup() {
        jdbc.update("DELETE FROM orders WHERE buyer_id=?", BUYER);
        jdbc.update("DELETE FROM listings WHERE seller_id=?", SELLER);
        jdbc.update("DELETE FROM members WHERE id IN (?,?)", SELLER, BUYER);
    }

    private static double millis(long start) { return (System.nanoTime() - start) / 1_000_000.0; }

    public static class SqlCapture implements StatementInspector {
        static final ThreadLocal<Map<String, Long>> counts = new ThreadLocal<>();
        @Override public String inspect(String sql) {
            var current = counts.get();
            if (current != null) {
                String kind = sql.startsWith("update orders") ? "update_order"
                        : sql.startsWith("update listings") ? "update_listing"
                        : sql.contains("from orders") && sql.contains("for update") ? "lock_order"
                        : sql.contains("from listings") && sql.contains("for update") ? "lock_listing"
                        : sql.contains("from orders") ? "find_candidates" : "other";
                current.merge(kind, 1L, Long::sum);
            }
            return sql;
        }
    }
}
