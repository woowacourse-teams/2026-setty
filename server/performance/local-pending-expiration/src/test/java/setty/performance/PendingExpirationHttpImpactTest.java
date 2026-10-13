package setty.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import setty.platform.order.service.PendingOrderExpirationScheduler;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

/** Paired real HTTP comparison. The only treatment is invoking the unchanged expiration job. */
@Tag("local-pending-expiration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.otlp.metrics.export.enabled=false",
        "setty.events.resubmission-interval=PT24H",
        "setty.storage.listing-images.public-base-url=https://images.example.test",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "setty.performance.PendingExpirationWorkloadTest$SqlCapture"
})
public class PendingExpirationHttpImpactTest extends MySqlIntegrationTestSupport {
    private static final int INVENTORY = 5_000;
    private static final int DUE = 500;
    private static final int JOURNEYS = 250;
    private static final int INTERVAL_MS = 120; // 500 journeys/minute; two API requests per journey.
    private static final int DURATION_MS = JOURNEYS * INTERVAL_MS;
    private static final int EXPIRATION_OFFSET_MS = 10_000;
    private static final long SELLER = 940001;
    private static final long COHORT_BUYER = 940002;
    private static final long BUYER_BASE = 941000;
    private static final Instant REFERENCE = Instant.parse("2026-10-12T12:00:00Z");

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private PendingOrderExpirationScheduler scheduler;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private Clock clock;

    private final List<Map<String, Object>> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> poolSamples = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> runs = new ArrayList<>();
    private final Path output = Path.of(System.getProperty("expiration.benchmark.output")).toAbsolutePath();

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void compareRequestsWithAndWithoutExpiration() throws Exception {
        Files.createDirectories(output);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("setty_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings", Integer.class)).isZero();
        when(clock.instant()).thenReturn(REFERENCE);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1).build()) {
            seedInventory();
            resetCohort();
            var smoke = client.send(HttpRequest.newBuilder(uri("/api/listings")).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(smoke.statusCode()).withFailMessage("Smoke response: %s", new String(smoke.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(200);
            assertThat(mapper.readTree(smoke.body()).get("items").size()).isEqualTo(INVENTORY);
            long warmup = System.nanoTime();
            for (int n = 0; n < 20; n++) journey(client, "warmup", 0, n, warmup);
            for (int pair = 1; pair <= 3; pair++) {
                var conditions = pair % 2 == 0 ? List.of(true, false) : List.of(false, true);
                for (boolean expire : conditions) run(client, pair, expire);
            }
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("mysql", jdbc.queryForObject("SELECT VERSION()", String.class));
            metadata.put("java", System.getProperty("java.version"));
            metadata.put("inventory", INVENTORY);
            metadata.put("images_per_listing", 1);
            metadata.put("due_cohort", DUE);
            metadata.put("journeys_per_run", JOURNEYS);
            metadata.put("journey_interval_ms", INTERVAL_MS);
            metadata.put("arrival_basis", "Previously selected 500 orders/minute burst rate. Each journey reads the listing collection once, then creates one order on its own distinct unused listing.");
            metadata.put("duration_ms", DURATION_MS);
            metadata.put("expiration_offset_ms", EXPIRATION_OFFSET_MS);
            metadata.put("paired_repetitions", 3);
            metadata.put("hikari_maximum_pool_size", dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize());
            metadata.put("heap_max_bytes", Runtime.getRuntime().maxMemory());
            metadata.put("available_processors", Runtime.getRuntime().availableProcessors());
            metadata.put("request_timeout_seconds", 10);
            metadata.put("scope", "Actual localhost HTTP/MVC/auth/JPA and temporary MySQL. Load generator and application share a JVM/Mac. No external payment or image downloads. No same-listing purchase competition.");
            metadata.put("clock", "Fixed application Clock: existing 500 orders are due; newly created orders expire six minutes later. Automatic schedule waits are disabled; the real expiration method runs once at t=10s only in treatment.");
            metadata.put("window_comparison", "For each pair use treatment's actual expiration start/end offsets to select requests STARTING in the same relative window in both conditions. Also retain full-run measurements.");
            metadata.put("verification", "All HTTP status/request-id/created-order checks and post-run database invariants passed.");
            Files.writeString(output.resolve("metadata.json"), mapper.writeValueAsString(metadata));
        } finally {
            save();
            cleanup();
        }
    }

    private void run(HttpClient client, int pair, boolean expire) throws Exception {
        resetCohort();
        String condition = expire ? "with_expiration" : "without_expiration";
        var failed = new AtomicBoolean();
        var tasks = new ConcurrentLinkedQueue<CompletableFuture<Void>>();
        var batch = new CompletableFuture<Map<String, Object>>();
        var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        long start = System.nanoTime();
        long gcBefore = gcMillis();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor();
             var timer = Executors.newSingleThreadScheduledExecutor();
             var sampler = Executors.newSingleThreadScheduledExecutor()) {
            sampler.scheduleAtFixedRate(() -> poolSamples.add(Map.of(
                    "pair", pair, "condition", condition, "offset_ms", millis(start),
                    "active", pool.getActiveConnections(), "waiting", pool.getThreadsAwaitingConnection(),
                    "total", pool.getTotalConnections())), 0, 20, TimeUnit.MILLISECONDS);
            if (expire) {
                timer.schedule(() -> workers.submit(() -> {
                    try {
                        double offset = millis(start);
                        PendingExpirationWorkloadTest.SqlCapture.counts.set(new TreeMap<>());
                        long jobStart = System.nanoTime();
                        scheduler.expirePendingOrders();
                        double elapsed = millis(jobStart);
                        var sql = new TreeMap<>(PendingExpirationWorkloadTest.SqlCapture.counts.get());
                        PendingExpirationWorkloadTest.SqlCapture.counts.remove();
                        batch.complete(Map.of("start_ms", offset, "end_ms", millis(start),
                                "job_ms", elapsed, "hibernate_sql_by_kind", sql,
                                "hibernate_sql", sql.values().stream().mapToLong(Long::longValue).sum()));
                    } catch (Throwable e) { failed.set(true); batch.completeExceptionally(e); }
                }), EXPIRATION_OFFSET_MS, TimeUnit.MILLISECONDS);
            } else batch.complete(Map.of());
            for (int n = 0; n < JOURNEYS; n++) {
                final int number = n;
                timer.schedule(() -> {
                    if (failed.get()) return;
                    tasks.add(CompletableFuture.runAsync(() -> {
                        try { journey(client, condition, pair, number, start); }
                        catch (Exception | AssertionError e) { failed.set(true); throw new RuntimeException(e); }
                    }, workers));
                }, n * (long) INTERVAL_MS, TimeUnit.MILLISECONDS);
            }
            timer.schedule(() -> {}, DURATION_MS, TimeUnit.MILLISECONDS).get(45, TimeUnit.SECONDS);
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);
            Map<String, Object> expiration = batch.get(15, TimeUnit.SECONDS);
            assertThat(failed.get()).isFalse();
            assertThat(tasks).hasSize(JOURNEYS);
            var result = new LinkedHashMap<String, Object>();
            result.put("pair", pair);
            result.put("condition", condition);
            result.put("elapsed_ms", millis(start));
            result.put("expiration", expiration);
            result.put("gc_collection_ms", gcMillis() - gcBefore);
            runs.add(result);
        }
        verify(expire);
        save();
        System.out.println("PENDING_EXPIRATION_HTTP_RUN " + mapper.writeValueAsString(runs.getLast()));
    }

    private void journey(HttpClient client, String condition, int pair, int n, long runStart) throws Exception {
        double journeyStarted = millis(runStart);
        send(client, "list", condition, pair, n, runStart, journeyStarted,
                HttpRequest.newBuilder(uri("/api/listings")).GET(), 200);
        send(client, "create", condition, pair, n, runStart, journeyStarted,
                HttpRequest.newBuilder(uri("/api/orders"))
                        .header("Authorization", "Bearer " + token(n))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"listingId\":" + (1001 + n) + "}")), 201);
    }

    private void send(HttpClient client, String kind, String condition, int pair, int n,
                      long runStart, double journeyStart, HttpRequest.Builder builder, int expected) throws Exception {
        String requestId = "expiry-http-" + pair + "-" + condition + "-" + n + "-" + kind;
        long start = System.nanoTime();
        double offset = millis(runStart);
        var row = new LinkedHashMap<String, Object>();
        row.put("pair", pair); row.put("condition", condition); row.put("journey", n); row.put("kind", kind);
        row.put("client_label", requestId); row.put("scheduled_journey_ms", n * INTERVAL_MS);
        row.put("journey_start_ms", journeyStart); row.put("start_ms", offset);
        try {
            var response = client.send(builder.header("X-Request-Id", requestId)
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofByteArray());
            row.put("client_ms", millis(start)); row.put("end_ms", millis(runStart));
            row.put("status", response.statusCode()); row.put("response_bytes", response.body().length);
            assertThat(response.statusCode()).isEqualTo(expected);
            String serverRequestId = response.headers().firstValue("X-Request-Id").orElseThrow();
            assertThat(serverRequestId).isNotBlank();
            row.put("request_id", serverRequestId);
            if (kind.equals("create")) {
                var body = mapper.readTree(response.body());
                assertThat(body.get("listingId").asLong()).isEqualTo(1001 + n);
                assertThat(body.get("buyerId").asLong()).isEqualTo(BUYER_BASE + n);
                row.put("created_order_id", body.get("id").asLong());
            }
        } catch (Exception | AssertionError e) {
            row.put("error", e.toString()); row.put("end_ms", millis(runStart));
            row.putIfAbsent("client_ms", millis(start));
            throw e;
        } finally { requests.add(row); }
    }

    private void seedInventory() {
        jdbc.update("INSERT INTO members(id,login_id,password,role,phone_number,address) VALUES (?, 'http-seller', REPEAT('!',60), 'MEMBER','000-0000-0000','LOCAL'), (?, 'http-cohort', REPEAT('!',60), 'MEMBER','000-0000-0000','LOCAL')", SELLER, COHORT_BUYER);
        var buyers = new ArrayList<Object[]>();
        for (int n = 0; n < JOURNEYS; n++) buyers.add(new Object[]{BUYER_BASE + n, "http-buyer-" + n, token(n)});
        jdbc.batchUpdate("INSERT INTO members(id,login_id,password,role,phone_number,address,token) VALUES (?,?,REPEAT('!',60),'MEMBER','000-0000-0000','LOCAL',?)", buyers);
        for (int base = 1; base <= INVENTORY; base += 100) {
            var rows = new ArrayList<String>(); var args = new ArrayList<Object>();
            var images = new ArrayList<String>(); var imageArgs = new ArrayList<Object>();
            for (int id = base; id < base + 100; id++) {
                rows.add("(?,?,'[LOCAL] HTTP fixture','Synthetic data',50000,10000,'DESK','A',80,50,70,'AVAILABLE',FALSE,'2026-10-12 11:00:00','2026-10-12 11:00:00')");
                args.add(id); args.add(SELLER);
                images.add("(?,?,1)"); imageArgs.add(id); imageArgs.add("setty/images/listings/local-http-" + id + ".jpg");
            }
            jdbc.update("INSERT INTO listings(id,seller_id,title,description,price,delivery_fee,category,condition_grade,width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at) VALUES " + String.join(",", rows), args.toArray());
            jdbc.update("INSERT INTO listing_images(listing_id,object_key,display_order) VALUES " + String.join(",", images), imageArgs.toArray());
        }
        jdbc.execute("ANALYZE TABLE listings, listing_images, members");
    }

    private void resetCohort() {
        jdbc.update("DELETE FROM orders WHERE buyer_id=? OR buyer_id BETWEEN ? AND ?", COHORT_BUYER, BUYER_BASE, BUYER_BASE + JOURNEYS - 1);
        jdbc.update("UPDATE listings SET has_purchase_request=(id<=?), sale_status='AVAILABLE', updated_at='2026-10-12 11:00:00' WHERE seller_id=?", DUE, SELLER);
        var rows = new ArrayList<Object[]>();
        for (int id = 1; id <= DUE; id++) rows.add(new Object[]{id, id, COHORT_BUYER});
        jdbc.batchUpdate("INSERT INTO orders(id,listing_id,buyer_id,delivery_status,order_status,pending_expires_at) VALUES (?,?,?,'PENDING','PENDING','2026-10-12 11:59:59')", rows);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(DUE);
    }

    private void verify(boolean expired) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(DUE + JOURNEYS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id=? AND order_status=?", Integer.class, COHORT_BUYER, expired ? "EXPIRED" : "PENDING")).isEqualTo(DUE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE id<=? AND has_purchase_request=?", Integer.class, DUE, !expired)).isEqualTo(DUE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id BETWEEN ? AND ? AND order_status='PENDING' AND pending_expires_at='2026-10-12 12:06:00'", Integer.class, BUYER_BASE, BUYER_BASE + JOURNEYS - 1)).isEqualTo(JOURNEYS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE id BETWEEN 1001 AND ? AND has_purchase_request=TRUE", Integer.class, 1000 + JOURNEYS)).isEqualTo(JOURNEYS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE seller_id=?", Integer.class, SELLER)).isEqualTo(INVENTORY);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing_images", Integer.class)).isEqualTo(INVENTORY);
    }

    private void save() throws Exception {
        Files.writeString(output.resolve("requests.json"), mapper.writeValueAsString(requests));
        Files.writeString(output.resolve("pool-samples.json"), mapper.writeValueAsString(poolSamples));
        Files.writeString(output.resolve("runs.json"), mapper.writeValueAsString(runs));
    }

    private void cleanup() {
        jdbc.update("DELETE FROM orders WHERE buyer_id=? OR buyer_id BETWEEN ? AND ?", COHORT_BUYER, BUYER_BASE, BUYER_BASE + JOURNEYS - 1);
        jdbc.update("DELETE FROM listing_images WHERE listing_id BETWEEN 1 AND ?", INVENTORY);
        jdbc.update("DELETE FROM listings WHERE seller_id=?", SELLER);
        jdbc.update("DELETE FROM members WHERE id IN (?,?) OR id BETWEEN ? AND ?", SELLER, COHORT_BUYER, BUYER_BASE, BUYER_BASE + JOURNEYS - 1);
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private static String token(int n) { return String.format("00000000-0000-0000-0000-%012d", n); }
    private static double millis(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }
}
