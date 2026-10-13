package setty.performance;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import setty.platform.order.domain.OrderStatus;
import setty.platform.order.repository.OrderRepository;
import setty.platform.order.service.PendingOrderExpirationService;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

/** Varies only chunk size around the production repository and transactional service. */
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
public class PendingExpirationBatchSizeTest extends MySqlIntegrationTestSupport {

    private static final long SELLER = 940001;
    private static final long BUYER = 940002;
    private static final int INVENTORY = 5_000;
    private static final int DUE = 500;
    private static final Instant REFERENCE = Instant.parse("2026-10-13T00:00:00Z");
    // Each size appears equally often in every execution position; all directed adjacent pairs are balanced.
    private static final List<List<Integer>> ORDERS = Boolean.getBoolean("expiration.benchmark.extended") ? List.of(
            List.of(200, 300, 500, 400), List.of(300, 400, 200, 500),
            List.of(400, 500, 300, 200), List.of(500, 200, 400, 300)) : List.of(
            List.of(50, 100, 200), List.of(200, 100, 50),
            List.of(100, 50, 200), List.of(200, 50, 100),
            List.of(50, 200, 100), List.of(100, 200, 50));

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private OrderRepository orders;
    @Autowired private PendingOrderExpirationService expiration;
    @Autowired private ObjectMapper mapper;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void compareBatchSizesWithBalancedExecutionOrder() throws Exception {
        Path output = Path.of(System.getProperty("expiration.benchmark.output")).toAbsolutePath();
        Files.createDirectories(output);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("setty_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings", Integer.class)).isZero();
        List<Map<String, Object>> samples = new ArrayList<>();
        try {
            seedInventory();
            // Three warmups per size. Fixture reset and all assertions are outside measured time.
            for (int warmup = 0; warmup < 3; warmup++) {
                for (int size : ORDERS.get(warmup)) {
                    resetOrders();
                    measure(size, -1, warmup);
                    verify();
                }
            }
            for (int round = 0; round < 12; round++) {
                List<Integer> sequence = ORDERS.get(round % ORDERS.size());
                for (int position = 0; position < sequence.size(); position++) {
                    int size = sequence.get(position);
                    resetOrders();
                    Map<String, Object> sample = measure(size, round + 1, position + 1);
                    verify();
                    samples.add(sample);
                    System.out.println("PENDING_EXPIRATION_BATCH_SIZE " + mapper.writeValueAsString(sample));
                }
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("mysql", jdbc.queryForObject("SELECT VERSION()", String.class));
            metadata.put("java", System.getProperty("java.version"));
            metadata.put("inventory", INVENTORY);
            metadata.put("due_orders", DUE);
            metadata.put("batch_sizes", ORDERS.getFirst().stream().sorted().toList());
            metadata.put("measured_runs_per_size", 12);
            metadata.put("warmups_per_size", 3);
            metadata.put("execution_orders", ORDERS);
            metadata.put("method", "Same production candidate query and proxied expireBatch service; test coordinates identical sequential chunks with variable size. All chunks must succeed; no fallback in the measured path.");
            metadata.put("release_timing", "Service return occurs after transaction commit. Per-listing release is approximated by its chunk return time relative to job start; no HTTP request or exact database lock timing is measured.");
            metadata.put("selection_rule", "Compare paired total time, first release and listing-weighted mean release across balanced rounds. If differences are small relative to run variation, prefer a smaller lock scope. No production SLO or universal optimal size is claimed.");
            metadata.put("scope", "Local isolated temporary MySQL; same JVM for all sizes. No HTTP, external payment, deliberate row contention, six-minute aging or scheduling delay.");
            Files.writeString(output.resolve("metadata.json"), mapper.writeValueAsString(metadata));
        } finally {
            Files.writeString(output.resolve("samples.json"), mapper.writeValueAsString(samples));
            jdbc.update("DELETE FROM orders WHERE buyer_id=?", BUYER);
            jdbc.update("DELETE FROM listings WHERE seller_id=?", SELLER);
            jdbc.update("DELETE FROM members WHERE id IN (?,?)", SELLER, BUYER);
        }
    }

    private Map<String, Object> measure(int size, int round, int position) {
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        PendingExpirationWorkloadTest.SqlCapture.counts.set(new TreeMap<>());
        List<Map<String, Object>> chunks = new ArrayList<>();
        int expired = 0;
        double weightedRelease = 0;
        long started = System.nanoTime();
        List<Long> candidates = orders.findExpiredOrderIds(OrderStatus.PENDING, REFERENCE);
        double lookupMs = millis(started);
        for (int from = 0; from < candidates.size(); from += size) {
            List<Long> ids = candidates.subList(from, Math.min(from + size, candidates.size()));
            long chunkStarted = System.nanoTime();
            int changed = expiration.expireBatch(ids, REFERENCE);
            double duration = millis(chunkStarted);
            double release = millis(started);
            expired += changed;
            weightedRelease += release * changed;
            chunks.add(Map.of("count", changed, "transaction_ms", duration, "release_offset_ms", release));
        }
        double elapsed = millis(started);
        var sql = new TreeMap<>(PendingExpirationWorkloadTest.SqlCapture.counts.get());
        PendingExpirationWorkloadTest.SqlCapture.counts.remove();
        long statements = sql.values().stream().mapToLong(Long::longValue).sum();
        int expectedChunks = (DUE + size - 1) / size;
        assertThat(candidates).hasSize(DUE);
        assertThat(expired).isEqualTo(DUE);
        assertThat(statements).isEqualTo(1 + 4L * expectedChunks);
        assertThat(stats.getTransactionCount()).isEqualTo(expectedChunks);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("round", round);
        sample.put("position", position);
        sample.put("batch_size", size);
        sample.put("job_ms", elapsed);
        sample.put("lookup_ms", lookupMs);
        sample.put("first_release_ms", chunks.getFirst().get("release_offset_ms"));
        sample.put("listing_weighted_mean_release_ms", weightedRelease / DUE);
        sample.put("chunks", chunks);
        sample.put("hibernate_sql", statements);
        sample.put("hibernate_sql_by_kind", sql);
        sample.put("hibernate_transactions", stats.getTransactionCount());
        return sample;
    }

    private void seedInventory() {
        jdbc.update("""
                INSERT INTO members(id,login_id,password,role,phone_number,address)
                VALUES (?, 'batch-size-seller', REPEAT('!',60), 'MEMBER', '000-0000-0000', 'LOCAL EXPERIMENT'),
                       (?, 'batch-size-buyer', REPEAT('!',60), 'MEMBER', '000-0000-0000', 'LOCAL EXPERIMENT')
                """, SELLER, BUYER);
        for (int base = 1; base <= INVENTORY; base += 100) {
            var args = new ArrayList<Object>();
            var rows = new ArrayList<String>();
            for (int id = base; id < base + 100 && id <= INVENTORY; id++) {
                rows.add("(?,?,'[LOCAL] Batch size','Synthetic data',50000,10000,'DESK','A',80,50,70,'AVAILABLE',FALSE,'2026-10-12 08:00:00','2026-10-12 08:00:00')");
                args.add(id);
                args.add(SELLER);
            }
            jdbc.update("INSERT INTO listings(id,seller_id,title,description,price,delivery_fee,category,condition_grade,width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at) VALUES "
                    + String.join(",", rows), args.toArray());
        }
    }

    private void resetOrders() {
        jdbc.update("DELETE FROM orders WHERE buyer_id=?", BUYER);
        jdbc.update("UPDATE listings SET has_purchase_request=TRUE, updated_at='2026-10-12 08:00:00' WHERE seller_id=? AND id<=?", SELLER, DUE);
        for (int base = 1; base <= DUE; base += 100) {
            var args = new ArrayList<Object>();
            var rows = new ArrayList<String>();
            for (int id = base; id < base + 100 && id <= DUE; id++) {
                rows.add("(?,?,?,'PENDING','PENDING','2026-10-12 23:59:59')");
                args.add(id);
                args.add(id);
                args.add(BUYER);
            }
            jdbc.update("INSERT INTO orders(id,listing_id,buyer_id,delivery_status,order_status,pending_expires_at) VALUES "
                    + String.join(",", rows), args.toArray());
        }
    }

    private void verify() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(DUE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_status='EXPIRED' AND delivery_status='PENDING'", Integer.class))
                .isEqualTo(DUE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE seller_id=? AND has_purchase_request=FALSE AND sale_status='AVAILABLE'", Integer.class, SELLER))
                .isEqualTo(INVENTORY);
    }

    private static double millis(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }
}
