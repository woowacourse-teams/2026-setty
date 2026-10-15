package setty.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import setty.global.logging.ListingRequestTiming;
import setty.platform.listing.domain.ListingImage;
import setty.platform.listing.repository.ListingImageRepository;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

/** Opt-in experiment: real HTTP/MVC/JPA/MySQL; only the image-query choice is switched by a test spy. */
@Tag("local-thumbnail-query")
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.otlp.metrics.export.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.cache.use_query_cache=false",
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "setty.performance.ThumbnailQueryComparisonTest$SqlCapture",
        "setty.storage.listing-images.bucket=local-thumbnail-experiment",
        "setty.storage.listing-images.public-base-url=https://images.example.test"
})
public class ThumbnailQueryComparisonTest extends MySqlIntegrationTestSupport {
    private static final int FIXTURE_LISTINGS = 5_000;
    private static final int EXTRA_LISTINGS = 20;
    private static final int TOTAL_LISTINGS = FIXTURE_LISTINGS + EXTRA_LISTINGS;
    private static final int WARMUP = 20;
    private static final int RUNS = 3;
    private static final int MEASUREMENTS = 100;
    private static final long SELLER_ID = 987_650L;
    private static final String BASELINE = "min-order";
    private static final String CANDIDATE = "order-one";
    private static final String BASELINE_JPQL = """
            select image from ListingImage image
            where image.listingId in :listingIds
              and image.displayOrder = (
                  select min(candidate.displayOrder) from ListingImage candidate
                  where candidate.listingId = image.listingId
              )
            """;
    private static final AtomicReference<String> variant = new AtomicReference<>(CANDIDATE);
    private static final Map<String, String> sqlByVariant = new ConcurrentHashMap<>();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private ListingImageRepository imageRepository;

    private final Map<String, Map<String, Object>> timings = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> samples = new ArrayList<>();
    private final Path output = Path.of(System.getProperty("thumbnail.benchmark.output")).toAbsolutePath();
    private String expectedBodyHash;
    private int photos;

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void compareCurrentMinimumOrderWithFirstOrderOnTheSameLocalDataset() throws Exception {
        Files.createDirectories(output);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("setty_test");
        assertThat(imageRepository.getClass()).isNotNull();
        String candidateQuery = ListingImageRepository.class
                .getMethod("findThumbnailsByListingIdIn", Collection.class).getAnnotation(Query.class).value();
        assertThat(candidateQuery.replaceAll("\\s+", " "))
                .contains("image.displayOrder = 1").doesNotContain("min(");

        // Keep the dispatch path identical: both use real Hibernate queries in the service transaction.
        // The candidate JPQL is read from the real repository annotation; normal tests cover that adapter.
        doAnswer(invocation -> {
            String jpql = BASELINE.equals(variant.get()) ? BASELINE_JPQL : candidateQuery;
            return entityManager.createQuery(jpql, ListingImage.class)
                    .setParameter("listingIds", invocation.getArgument(0, Collection.class)).getResultList();
        }).when(imageRepository).findThumbnailsByListingIdIn(anyCollection());

        Logger logger = (Logger) LoggerFactory.getLogger(ListingRequestTiming.class);
        AppenderBase<ILoggingEvent> capture = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("requestId", event.getMDCPropertyMap().get("requestId"));
                fields.put("logged_at", Instant.ofEpochMilli(event.getTimeStamp()).toString());
                event.getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
                assertThat(timings.putIfAbsent((String) fields.get("requestId"), fields)).isNull();
            }
        };
        capture.start();
        logger.addAppender(capture);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1).build()) {
            seedListings();
            writeMetadata(candidateQuery);
            for (int imageCount : List.of(1, 5)) {
                photos = imageCount;
                seedImages();
                verifyFixture();
                for (String name : List.of(BASELINE, CANDIDATE)) {
                    request(client, name, "preflight", 0, 0);
                    writePlans(name);
                }
                for (int pair = 1; pair <= WARMUP; pair++) {
                    for (String name : order(pair)) request(client, name, "warmup", 0, pair);
                }
                for (int run = 1; run <= RUNS; run++) {
                    for (int pair = 1; pair <= MEASUREMENTS; pair++) {
                        for (String name : order(run + pair)) request(client, name, "measurement", run, pair);
                    }
                    System.out.printf("THUMBNAIL_COMPARISON photos=%d run=%d: %d paired requests complete%n",
                            photos, run, MEASUREMENTS);
                }
            }
            assertThat(samples).hasSize(2 * (2 + 2 * WARMUP + 2 * RUNS * MEASUREMENTS));
            assertThat(timings).hasSize(samples.size());
            writeSamples();
            Files.writeString(output.resolve("summary.json"), mapper.writeValueAsString(summary()));
            System.out.println("THUMBNAIL_COMPARISON passed; output=" + output);
        } finally {
            logger.detachAppender(capture);
            capture.stop();
            // Only this dedicated seller's data is removed from the disposable Testcontainers DB.
            jdbc.update("DELETE i FROM listing_images i JOIN listings l ON l.id=i.listing_id WHERE l.seller_id=?", SELLER_ID);
            jdbc.update("DELETE FROM listings WHERE seller_id=?", SELLER_ID);
            jdbc.update("DELETE FROM members WHERE id=?", SELLER_ID);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE seller_id=?", Integer.class, SELLER_ID))
                    .isZero();
            if (!samples.isEmpty()) writeSamples();
        }
    }

    private static List<String> order(int pair) {
        return pair % 2 == 0 ? List.of(BASELINE, CANDIDATE) : List.of(CANDIDATE, BASELINE);
    }

    private void seedListings() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings", Integer.class)).isZero();
        jdbc.update("""
                INSERT INTO members(id,login_id,password,role,phone_number,address)
                VALUES (?, 'local-thumbnail', REPEAT('!',60), 'MEMBER', '000-0000-0000', 'LOCAL EXPERIMENT ONLY')
                """, SELLER_ID);
        List<Object[]> rows = IntStream.rangeClosed(1, TOTAL_LISTINGS).mapToObj(n -> new Object[]{
                n, SELLER_ID, String.format(Locale.ROOT, "[LOCAL] Test desk %06d", n),
                Timestamp.from(Instant.parse("2026-10-01T00:00:00Z").plusSeconds(n))
        }).toList();
        jdbc.batchUpdate("""
                INSERT INTO listings(id,seller_id,title,description,price,delivery_fee,category,condition_grade,
                  width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at,deleted_at)
                VALUES (?,?,?,'Synthetic local comparison. Not for purchase.',50000,10000,'DESK','A',
                  80,50,70,'AVAILABLE',FALSE,?, '2026-10-01 00:00:00',NULL)
                """, rows);
    }

    private void seedImages() {
        jdbc.update("DELETE FROM listing_images WHERE listing_id BETWEEN 1 AND ?", TOTAL_LISTINGS);
        List<Object[]> rows = new ArrayList<>();
        for (int id = 1; id <= TOTAL_LISTINGS; id++) {
            int count = id <= FIXTURE_LISTINGS ? photos : 1;
            for (int displayOrder = 1; displayOrder <= count; displayOrder++) {
                rows.add(new Object[]{id, String.format(Locale.ROOT,
                        "setty/images/listings/local-%06d-%02d.jpg", id, displayOrder), displayOrder});
            }
        }
        jdbc.batchUpdate("INSERT INTO listing_images(listing_id,object_key,display_order) VALUES (?,?,?)", rows);
        jdbc.execute("ANALYZE TABLE listings, listing_images");
    }

    private void verifyFixture() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings", Integer.class)).isEqualTo(TOTAL_LISTINGS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing_images", Integer.class))
                .isEqualTo(FIXTURE_LISTINGS * photos + EXTRA_LISTINGS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listing_images WHERE display_order=1", Integer.class))
                .isEqualTo(TOTAL_LISTINGS);
    }

    private void request(HttpClient client, String name, String phase, int run, int pair) throws Exception {
        variant.set(name);
        Instant startedAt = Instant.now();
        long started = System.nanoTime();
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/listings"))
                .timeout(java.time.Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        double elapsed = (System.nanoTime() - started) / 1_000_000.0;
        assertThat(response.statusCode()).isEqualTo(200);
        String id = response.headers().firstValue("X-Request-Id").orElseThrow();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.body()));
        if (expectedBodyHash == null) {
            expectedBodyHash = hash;
            assertThat(mapper.readTree(response.body()).get("items").size()).isEqualTo(TOTAL_LISTINGS);
            Files.write(output.resolve("response-baseline.json"), response.body());
        }
        assertThat(hash).isEqualTo(expectedBodyHash);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!timings.containsKey(id) && System.nanoTime() < deadline) Thread.sleep(2);
        Map<String, Object> timing = timings.get(id);
        assertThat(timing).isNotNull().containsEntry("status", 200).containsEntry("outcome", "completed")
                .containsEntry("response_complete", true).containsEntry("listing_count", TOTAL_LISTINGS)
                .containsEntry("image_count", TOTAL_LISTINGS).containsEntry("summary_count", TOTAL_LISTINGS)
                .containsEntry("listings_completed", true).containsEntry("images_completed", true)
                .containsEntry("mapping_completed", true);
        Map<String, Object> sample = new LinkedHashMap<>(timing);
        sample.put("variant", name);
        sample.put("photos", photos);
        sample.put("phase", phase);
        sample.put("run", run);
        sample.put("pair", pair);
        sample.put("started_at", startedAt.toString());
        sample.put("client_ms", elapsed);
        sample.put("response_bytes", response.body().length);
        sample.put("body_sha256", hash);
        samples.add(sample);
    }

    private void writePlans(String name) throws Exception {
        String sql = sqlByVariant.get(name);
        assertThat(sql).as("Historical JPA path not observed; use reproduce.py with the pinned source revision").isNotNull();
        if (BASELINE.equals(name)) assertThat(sql.toLowerCase(Locale.ROOT)).contains("min(");
        else assertThat(sql.toLowerCase(Locale.ROOT)).doesNotContain("min(");
        Object[] ids = IntStream.rangeClosed(1, TOTAL_LISTINGS).mapToObj(Long::valueOf).toArray();
        assertThat(sql.chars().filter(ch -> ch == '?').count()).isEqualTo(TOTAL_LISTINGS);
        String stem = "photos-" + photos + "-" + name;
        Files.writeString(output.resolve(stem + "-sql.txt"), sql);
        Files.writeString(output.resolve(stem + "-explain.json"),
                jdbc.queryForObject("EXPLAIN FORMAT=JSON " + sql, String.class, ids));
        List<String> analysis = jdbc.query("EXPLAIN ANALYZE " + sql, (rs, index) -> rs.getString(1), ids);
        Files.writeString(output.resolve(stem + "-analyze.txt"), String.join("\n", analysis));
    }

    private void writeMetadata(String candidate) throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("started_at", Instant.now().toString());
        metadata.put("fixture_listings", FIXTURE_LISTINGS);
        metadata.put("extra_synthetic_listings", EXTRA_LISTINGS);
        metadata.put("warmup_per_variant_per_condition", WARMUP);
        metadata.put("runs_per_condition", RUNS);
        metadata.put("measurements_per_variant_per_run", MEASUREMENTS);
        metadata.put("injection", "single sequential HTTP client; variants alternate; no arrival-rate/capacity test");
        metadata.put("baseline_jpql", BASELINE_JPQL);
        metadata.put("candidate_jpql", candidate);
        metadata.put("baseline_dispatch", "test spy executes retained MIN JPQL via real EntityManager in service transaction");
        metadata.put("candidate_dispatch", "same test spy and EntityManager path; JPQL read from real repository annotation");
        metadata.put("database", jdbc.queryForMap("SELECT VERSION() AS version, DATABASE() AS db_name, "
                + "@@innodb_buffer_pool_size AS buffer_pool_bytes, @@collation_server AS collation"));
        metadata.put("java", System.getProperty("java.version"));
        metadata.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        metadata.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        metadata.put("available_processors", Runtime.getRuntime().availableProcessors());
        Files.writeString(output.resolve("metadata.json"), mapper.writeValueAsString(metadata));
    }

    private void writeSamples() throws Exception {
        List<String> keys = List.of("photos", "variant", "phase", "run", "pair", "started_at", "requestId",
                "status", "client_ms", "filter_ms", "listings_ms", "images_ms", "mapping_ms",
                "listing_count", "image_count", "summary_count", "response_bytes", "body_sha256");
        StringBuilder csv = new StringBuilder(String.join(",", keys)).append('\n');
        StringBuilder jsonLines = new StringBuilder();
        for (Map<String, Object> sample : samples) {
            csv.append(keys.stream().map(key -> String.valueOf(sample.get(key)))
                    .collect(java.util.stream.Collectors.joining(","))).append('\n');
            jsonLines.append(mapper.writeValueAsString(sample)).append('\n');
        }
        Files.writeString(output.resolve("samples.csv"), csv.toString(), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("samples.jsonl"), jsonLines.toString(), StandardCharsets.UTF_8);
    }

    private Map<String, Object> summary() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int imageCount : List.of(1, 5)) for (int run = 1; run <= RUNS; run++) {
            final int selectedRun = run;
            for (String name : List.of(BASELINE, CANDIDATE)) {
                List<Map<String, Object>> group = samples.stream().filter(sample ->
                        sample.get("phase").equals("measurement") && sample.get("photos").equals(imageCount)
                                && sample.get("run").equals(selectedRun) && sample.get("variant").equals(name)).toList();
                assertThat(group).hasSize(MEASUREMENTS);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("photos", imageCount); row.put("run", run); row.put("variant", name); row.put("count", group.size());
                for (String metric : List.of("images_ms", "filter_ms", "client_ms")) {
                    List<Double> values = group.stream().map(s -> ((Number) s.get(metric)).doubleValue()).sorted().toList();
                    row.put(metric + "_median", (values.get(49) + values.get(50)) / 2.0);
                    row.put(metric + "_p95", values.get(94));
                    row.put(metric + "_max", values.getLast());
                }
                rows.add(row);
            }
        }
        return Map.of("completed", true, "total_requests", samples.size(), "body_sha256", expectedBodyHash,
                "rows", rows, "scope", "local warm-buffer HTTP experiment; not dev/prod performance evidence");
    }

    public static final class SqlCapture implements StatementInspector {
        @Override public String inspect(String sql) {
            if (sql.startsWith("select") && sql.contains("listing_images") && sql.contains(" in (")) {
                sqlByVariant.putIfAbsent(variant.get(), sql);
            }
            return sql;
        }
    }
}
