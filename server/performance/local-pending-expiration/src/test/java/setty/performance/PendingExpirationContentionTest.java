package setty.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import setty.platform.order.domain.OrderStatus;
import setty.platform.order.repository.OrderRepository;
import setty.platform.order.service.PendingOrderExpirationService;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

/** JDBC timing brackets, natural same-listing HTTP races, and separate deterministic interleavings. */
@Tag("local-pending-expiration")
@Import(PendingExpirationContentionTest.Instrumentation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.otlp.metrics.export.enabled=false",
        "setty.events.resubmission-interval=PT24H"
})
public class PendingExpirationContentionTest extends MySqlIntegrationTestSupport {
    private static final long SELLER = 950001, OLD_BUYER = 950002, NEW_BUYER = 950003;
    private static final int INVENTORY = 5_000;
    private static final String TOKEN = "11111111-1111-1111-1111-111111111111";
    private static final Instant REFERENCE = Instant.parse("2026-10-13T00:00:00Z");
    // Balanced Williams design: positions and directed adjacent pairs balanced over six rounds.
    private static final List<Integer> SIZES = List.of(50, 100, 200, 300, 400, 500);
    private static final int[] BASE_ORDER = {0, 1, 5, 2, 4, 3};
    private static final ThreadLocal<Trace> EXPIRATION = new ThreadLocal<>();
    private static final Map<String, String> HTTP_IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<String, List<Event>> HTTP_SQL = new ConcurrentHashMap<>();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderRepository orders;
    @Autowired private PendingOrderExpirationService expiration;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private Clock clock;
    @LocalServerPort private int port;
    private HttpClient client;
    private final List<Map<String, Object>> samples = new ArrayList<>();
    private final List<Map<String, Object>> probes = new ArrayList<>();

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void compareLockIntervalsAndSameListingOrders() throws Exception {
        Path output = Path.of(System.getProperty("expiration.benchmark.output")).toAbsolutePath();
        Files.createDirectories(output);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("setty_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        when(clock.instant()).thenReturn(REFERENCE);
        try (HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            client = http;
            seed();
            if (!Boolean.getBoolean("expiration.benchmark.probesOnly")) {
            for (int warmup = 0; warmup < 3; warmup++) {
                for (int size : SIZES) {
                    reset(500);
                    measure(500, size, -1, 0, true);
                }
            }
            for (int round = 0; round < 12; round++) {
                List<Integer> dues = round % 2 == 0 ? List.of(500, 1000) : List.of(1000, 500);
                for (int due : dues) {
                    for (boolean withHttp : round % 2 == 0 ? List.of(false, true) : List.of(true, false)) {
                        for (int position = 0; position < SIZES.size(); position++) {
                            int size = SIZES.get((BASE_ORDER[position] + round) % SIZES.size());
                            reset(due);
                            var sample = measure(due, size, round + 1, position + 1, withHttp);
                            samples.add(sample);
                        }
                    }
                }
                Files.writeString(output.resolve("samples.json"), mapper.writeValueAsString(samples));
                System.out.println("EXPIRATION_CONTENTION_ROUND " + (round + 1));
            }
            }
            // These deliberately hold a transaction for observation. Never mix their duration with performance samples.
            for (int size : SIZES) {
                reset(500);
                probes.add(controlledProbe(size));
                Files.writeString(output.resolve("controlled-probes.json"), mapper.writeValueAsString(probes));
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("mysql", jdbc.queryForObject("SELECT VERSION()", String.class));
            metadata.put("java", System.getProperty("java.version"));
            metadata.put("inventory", INVENTORY);
            metadata.put("due_counts", List.of(500, 1000));
            metadata.put("load_basis", "500 is the existing synthetic cohort, not a production maximum. 1000 doubles that cohort to test sensitivity to more than one 500-row chunk, not a forecast of real traffic.");
            metadata.put("sizes", SIZES);
            metadata.put("runs_per_cell", 12);
            metadata.put("warmups", "3 per size at N=500 with HTTP; same long-lived JVM and DB for all measured conditions");
            metadata.put("timing", "System.nanoTime around actual JDBC execute and commit. For each locked row, acquisition is within SELECT FOR UPDATE start/end and release within commit start/end. Report conservative hold brackets [commitStart-selectEnd, commitEnd-selectStart], not exact server-side row lock timestamps. Connection/transaction setup excluded from brackets, included in chunk duration.");
            metadata.put("natural_http", "Exactly two real POST /api/orders per job, launched asynchronously after order lock SELECT returns in first/last chunk, aimed at distinct first/last candidate listings. No forced pause. Capture the actual HTTP existence-check SQL interval relative to the relevant expiry commit. Retry rejections after job completion.");
            metadata.put("controlled_probes", "Hold after actual listing UPDATE and before commit; assert same-listing HTTP 400 completes while locks remain held, other-listing HTTP 201, and post-commit same-listing retry 201. Artificial duration excluded from performance results.");
            metadata.put("scope", "Local temporary MySQL, no payment call, no scheduled one-minute wait, no throughput or production latency guarantee. Candidate lookup and real proxied service match scheduler; test loop varies batch size, no fallback expected.");
            Files.writeString(output.resolve("metadata.json"), mapper.writeValueAsString(metadata));
        } finally {
            EXPIRATION.remove();
            Files.writeString(output.resolve("samples.json"), mapper.writeValueAsString(samples));
            Files.writeString(output.resolve("controlled-probes.json"), mapper.writeValueAsString(probes));
            jdbc.update("DELETE FROM orders WHERE buyer_id IN (?,?)", OLD_BUYER, NEW_BUYER);
            jdbc.update("DELETE FROM listings WHERE seller_id=?", SELLER);
            jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", SELLER, OLD_BUYER, NEW_BUYER);
        }
    }

    private Map<String, Object> measure(int due, int size, int round, int position, boolean withHttp) throws Exception {
        List<Trace> traces = new ArrayList<>();
        List<Map<String, Object>> chunks = new ArrayList<>();
        List<CompletableFuture<Map<String, Object>>> pending = new ArrayList<>();
        long start = System.nanoTime();
        List<Long> candidates = orders.findExpiredOrderIds(OrderStatus.PENDING, REFERENCE);
        assertThat(candidates).hasSize(due);
        double lookup = ms(System.nanoTime() - start);
        int count = (due + size - 1) / size;
        for (int from = 0; from < due; from += size) {
            int index = from / size;
            List<Long> ids = candidates.subList(from, Math.min(from + size, due));
            Trace trace = new Trace();
            if (withHttp) trace.onOrderLock = () -> {
                if (index == 0) pending.add(send(candidates.getFirst(), index, start, "natural-first"));
                if (index == count - 1) pending.add(send(candidates.getLast(), index, start, "natural-last"));
            };
            traces.add(trace);
            long began = System.nanoTime();
            EXPIRATION.set(trace);
            int changed;
            try { changed = expiration.expireBatch(ids, REFERENCE); }
            finally { EXPIRATION.remove(); }
            long ended = System.nanoTime();
            assertThat(changed).isEqualTo(ids.size());
            assertThat(trace.events).hasSize(4);
            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put("count", changed);
            chunk.put("transaction_ms", ms(ended - began));
            chunk.put("release_ms", ms(ended - start));
            chunk.put("commit_start_ms", ms(trace.commitStart - start));
            chunk.put("commit_end_ms", ms(trace.commitEnd - start));
            for (String kind : List.of("lock_order", "lock_listing")) {
                Event lock = trace.events.stream().filter(e -> e.kind.equals(kind)).findFirst().orElseThrow();
                assertThat(trace.commitStart).isGreaterThan(lock.end);
                chunk.put(kind + "_lower_ms", ms(trace.commitStart - lock.end));
                chunk.put(kind + "_upper_ms", ms(trace.commitEnd - lock.start));
            }
            chunks.add(chunk);
        }
        double elapsed = ms(System.nanoTime() - start);
        List<Map<String, Object>> requests = new ArrayList<>();
        for (var future : pending) {
            Map<String, Object> row = future.get(10, TimeUnit.SECONDS);
            int chunkIndex = (Integer) row.get("chunk_index");
            Trace trace = traces.get(chunkIndex);
            @SuppressWarnings("unchecked") List<Event> sql = (List<Event>) row.remove("raw_sql");
            Event exists = sql.stream().filter(e -> e.kind.equals("exists_order")).findFirst().orElseThrow();
            String visibility = exists.end < trace.commitStart ? "before_commit"
                    : exists.start > trace.commitEnd ? "after_commit" : "commit_boundary";
            row.put("visibility", visibility);
            row.put("exists_start_ms", ms(exists.start - start));
            row.put("exists_end_ms", ms(exists.end - start));
            row.put("listing_lock_sql", sql.stream().filter(e -> e.kind.equals("lock_listing")).count());
            int status = (Integer) row.get("status");
            assertThat(status).isIn(400, 201);
            if (visibility.equals("before_commit")) assertThat(status).isEqualTo(400);
            if (visibility.equals("after_commit")) assertThat(status).isEqualTo(201);
            if (status == 400) {
                assertThat(row.get("body").toString()).contains("ALREADY_ORDERED");
                assertThat(row.get("listing_lock_sql")).isEqualTo(0L);
                var retry = send(((Number) row.get("listing_id")).longValue(), chunkIndex, start, "after-rejection").get(10, TimeUnit.SECONDS);
                assertThat(retry.get("status")).isEqualTo(201);
                row.put("retry_status", retry.get("status"));
                row.put("retry_ms", retry.get("client_ms"));
            }
            requests.add(row);
        }
        int newOrders = withHttp ? 2 : 0;
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id=? AND order_status='EXPIRED'", Integer.class, OLD_BUYER)).isEqualTo(due);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id=? AND order_status='PENDING' AND pending_expires_at='2026-10-13 00:06:00'", Integer.class, NEW_BUYER)).isEqualTo(newOrders);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE seller_id=? AND has_purchase_request=TRUE", Integer.class, SELLER)).isEqualTo(newOrders);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("round", round); result.put("position", position); result.put("due", due);
        result.put("size", size); result.put("with_http", withHttp);
        result.put("job_ms", elapsed); result.put("lookup_ms", lookup);
        result.put("first_release_ms", chunks.getFirst().get("release_ms"));
        result.put("weighted_release_ms", chunks.stream().mapToDouble(c -> ((Number)c.get("release_ms")).doubleValue() * ((Number)c.get("count")).intValue()).sum() / due);
        result.put("sql", 1 + traces.stream().mapToInt(t -> t.events.size()).sum());
        result.put("transactions", count); result.put("chunks", chunks); result.put("requests", requests);
        return result;
    }

    private Map<String, Object> controlledProbe(int size) throws Exception {
        List<Long> ids = orders.findExpiredOrderIds(OrderStatus.PENDING, REFERENCE).subList(0, size);
        long target = ids.getFirst();
        CountDownLatch updated = new CountDownLatch(1), release = new CountDownLatch(1);
        Trace trace = new Trace();
        trace.onListingUpdate = () -> {
            updated.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Probe release timed out"); }
            catch (InterruptedException e) { throw new RuntimeException(e); }
        };
        long start = System.nanoTime();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var job = worker.submit(() -> {
                EXPIRATION.set(trace);
                try { return expiration.expireBatch(ids, REFERENCE); }
                finally { EXPIRATION.remove(); }
            });
            Map<String, Object> same, other;
            try {
                assertThat(updated.await(10, TimeUnit.SECONDS)).isTrue();
                same = send(target, 0, start, "controlled-before-commit").get(5, TimeUnit.SECONDS);
                assertThat(same.get("status")).isEqualTo(400);
                assertThat(same.get("body").toString()).contains("ALREADY_ORDERED");
                @SuppressWarnings("unchecked") List<Event> sql = (List<Event>) same.remove("raw_sql");
                assertThat(sql.stream().filter(e -> e.kind.equals("lock_listing")).count()).isZero();
                var unrelated = send(4999, 0, start, "controlled-other-listing");
                try {
                    other = unrelated.get(250, TimeUnit.MILLISECONDS);
                    other.remove("raw_sql");
                    assertThat(other.get("status")).isEqualTo(201);
                } catch (java.util.concurrent.TimeoutException pending) {
                    Map<String, Object> diagnostic = new LinkedHashMap<>();
                    diagnostic.put("in_flight", new LinkedHashMap<>(HTTP_IN_FLIGHT));
                    try (Connection ordinary = jdbc.getDataSource().getConnection();
                         Connection root = java.sql.DriverManager.getConnection(ordinary.getMetaData().getURL(), "root", "setty_test")) {
                        var rootJdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(root, true));
                        diagnostic.put("waits", rootJdbc.queryForList("""
                            SELECT r.OBJECT_NAME AS waiting_table, r.INDEX_NAME AS waiting_index,
                                   r.LOCK_TYPE AS waiting_type, r.LOCK_MODE AS waiting_mode, r.LOCK_DATA AS waiting_data,
                                   b.INDEX_NAME AS blocking_index, b.LOCK_MODE AS blocking_mode, b.LOCK_DATA AS blocking_data
                            FROM performance_schema.data_lock_waits w
                            JOIN performance_schema.data_locks r ON r.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND r.ENGINE=w.ENGINE
                            JOIN performance_schema.data_locks b ON b.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID AND b.ENGINE=w.ENGINE
                            """));
                        diagnostic.put("transactions", rootJdbc.queryForList("SELECT trx_state, trx_query, trx_rows_locked, trx_rows_modified FROM information_schema.innodb_trx"));
                        String in = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                        diagnostic.put("forced_select_plan", rootJdbc.queryForList("EXPLAIN SELECT * FROM orders FORCE INDEX(PRIMARY) WHERE id IN (" + in + ") ORDER BY id FOR UPDATE"));
                        diagnostic.put("forced_update_plan", rootJdbc.queryForList("EXPLAIN UPDATE orders FORCE INDEX(PRIMARY) SET order_status='EXPIRED' WHERE id IN (" + in + ") AND order_status='PENDING' AND pending_expires_at<='2026-10-13 00:00:00'"));
                        diagnostic.put("order_select_plan", rootJdbc.queryForList("EXPLAIN SELECT * FROM orders WHERE id IN (" + in + ") ORDER BY id FOR UPDATE"));
                        diagnostic.put("order_update_plan", rootJdbc.queryForList("EXPLAIN UPDATE orders SET order_status='EXPIRED' WHERE id IN (" + in + ") AND order_status='PENDING' AND pending_expires_at<='2026-10-13 00:00:00'"));
                    }
                    other = new LinkedHashMap<>();
                    other.put("status", "waiting_before_release");
                    other.put("diagnostic", diagnostic);
                    release.countDown();
                    Map<String, Object> completed = unrelated.get(5, TimeUnit.SECONDS);
                    completed.remove("raw_sql");
                    assertThat(completed.get("status")).isEqualTo(201);
                    other.put("after_release", completed);
                }
                if (!other.get("status").equals("waiting_before_release")) {
                    assertThat(job.isDone()).isFalse();
                    assertThat(trace.commitStart).isZero();
                }
            } finally { release.countDown(); }
            assertThat(job.get(10, TimeUnit.SECONDS)).isEqualTo(size);
            Map<String, Object> after = send(target, 0, start, "controlled-after-commit").get(5, TimeUnit.SECONDS);
            after.remove("raw_sql");
            assertThat(after.get("status")).isEqualTo(201);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE listing_id=? AND order_status='PENDING'", Integer.class, target)).isEqualTo(1);
            return Map.of("size", size, "before_commit", same, "other_listing", other, "after_commit", after,
                    "verified", "same-listing 400 completed before commit was allowed; no listing lock query; retry after commit 201; unrelated request recorded separately; artificial wait excluded from timing comparisons");
        }
    }

    private CompletableFuture<Map<String, Object>> send(long listingId, int chunk, long jobStart, String phase) {
        String label = phase + "-" + System.nanoTime();
        HTTP_SQL.put(label, new CopyOnWriteArrayList<>());
        long started = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/orders"))
                .header("Authorization", "Bearer " + TOKEN).header("Content-Type", "application/json")
                .header("X-Experiment-Probe", label).timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString("{\"listingId\":" + listingId + "}")).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            long ended = System.nanoTime();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("phase", phase); row.put("listing_id", listingId); row.put("chunk_index", chunk);
            row.put("start_ms", ms(started - jobStart)); row.put("end_ms", ms(ended - jobStart));
            row.put("client_ms", ms(ended - started)); row.put("status", response.statusCode());
            row.put("body", response.body()); row.put("raw_sql", HTTP_SQL.remove(label));
            return row;
        });
    }

    private void seed() {
        for (long id : List.of(SELLER, OLD_BUYER, NEW_BUYER)) jdbc.update("INSERT INTO members(id,login_id,password,role,phone_number,address,token) VALUES (?,?,REPEAT('!',60),'MEMBER','000-0000-0000','LOCAL',?)", id, "contention-" + id, id == NEW_BUYER ? TOKEN : "token-" + id);
        for (int base = 1; base <= INVENTORY; base += 100) {
            List<String> rows = new ArrayList<>(); List<Object> args = new ArrayList<>();
            for (int id = base; id < base + 100; id++) {
                rows.add("(?,?,'[LOCAL] Lock test','Synthetic data',50000,10000,'DESK','A',80,50,70,'AVAILABLE',FALSE,'2026-10-12 08:00:00','2026-10-12 08:00:00')");
                args.add(id); args.add(SELLER);
            }
            jdbc.update("INSERT INTO listings(id,seller_id,title,description,price,delivery_fee,category,condition_grade,width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at) VALUES " + String.join(",", rows), args.toArray());
        }
    }

    private void reset(int due) {
        jdbc.update("DELETE FROM orders WHERE buyer_id IN (?,?)", OLD_BUYER, NEW_BUYER);
        jdbc.update("UPDATE listings SET has_purchase_request=(id<=?), updated_at='2026-10-12 08:00:00' WHERE seller_id=?", due, SELLER);
        for (int base = 1; base <= due; base += 100) {
            List<String> rows = new ArrayList<>(); List<Object> args = new ArrayList<>();
            for (int id = base; id < base + 100 && id <= due; id++) {
                rows.add("(?,?,?,'PENDING','PENDING','2026-10-12 23:59:59')");
                args.add(id); args.add(id); args.add(OLD_BUYER);
            }
            jdbc.update("INSERT INTO orders(id,listing_id,buyer_id,delivery_status,order_status,pending_expires_at) VALUES " + String.join(",", rows), args.toArray());
        }
    }

    private static double ms(long nanos) { return nanos / 1_000_000.0; }
    private record Event(String kind, long start, long end) {}
    private static class Trace {
        final List<Event> events = new ArrayList<>();
        long commitStart, commitEnd;
        Runnable onOrderLock = () -> {}, onListingUpdate = () -> {};
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Instrumentation {
        @Bean static BeanPostProcessor jdbcTiming() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource source)) return bean;
                    return new DelegatingDataSource(source) {
                        @Override public Connection getConnection() throws SQLException { return wrap(super.getConnection()); }
                        @Override public Connection getConnection(String user, String password) throws SQLException { return wrap(super.getConnection(user, password)); }
                    };
                }
            };
        }
    }

    private static Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement")) {
                PreparedStatement statement = (PreparedStatement) invoke(method, connection, args);
                String sql = ((String) args[0]).toLowerCase(java.util.Locale.ROOT);
                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                    if (!m.getName().startsWith("execute")) return invoke(m, statement, a);
                    String requestLabel = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attr
                            ? attr.getRequest().getHeader("X-Experiment-Probe") : null;
                    if (requestLabel != null) HTTP_IN_FLIGHT.put(requestLabel, sql);
                    long start = System.nanoTime();
                    Object result;
                    try { result = invoke(m, statement, a); }
                    finally { if (requestLabel != null) HTTP_IN_FLIGHT.remove(requestLabel); }
                    long end = System.nanoTime();
                    String kind = kind(sql);
                    Trace trace = EXPIRATION.get();
                    if (trace != null) {
                        trace.events.add(new Event(kind, start, end));
                        if (kind.equals("lock_order")) trace.onOrderLock.run();
                        if (kind.equals("update_listing")) trace.onListingUpdate.run();
                    } else if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                        String label = attributes.getRequest().getHeader("X-Experiment-Probe");
                        List<Event> events = label == null ? null : HTTP_SQL.get(label);
                        if (events != null) events.add(new Event(kind, start, end));
                    }
                    return result;
                });
            }
            Trace trace = EXPIRATION.get();
            if (method.getName().equals("commit") && trace != null) {
                trace.commitStart = System.nanoTime();
                Object result = invoke(method, connection, args);
                trace.commitEnd = System.nanoTime();
                return result;
            }
            return invoke(method, connection, args);
        });
    }

    private static String kind(String sql) {
        if (sql.contains("for update") && sql.contains("from orders ")) return "lock_order";
        if (sql.contains("for update") && sql.contains("from listings ")) return "lock_listing";
        if (sql.startsWith("update orders ")) return "update_order";
        if (sql.startsWith("update listings ")) return "update_listing";
        if (sql.contains("from orders ") && sql.contains("order_status not in")) return "exists_order";
        return "other";
    }
    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }
}
