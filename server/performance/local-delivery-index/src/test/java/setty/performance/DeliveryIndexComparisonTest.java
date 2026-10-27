package setty.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.delivery.domain.DriverId;
import setty.delivery.persistence.JdbcDeliveryQueryRepository;
import tools.jackson.databind.ObjectMapper;

/** Local synthetic experiment, not a latency threshold for CI or evidence of dev API performance. */
@Tag("local-delivery-index")
@Testcontainers
class DeliveryIndexComparisonTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("delivery_index_test")
            .withCommand("--innodb-buffer-pool-size=128M", "--innodb-redo-log-capacity=64M");

    private static final int WARMUP = 2;
    private static final int MEASUREMENTS = 6;

    private enum Mode {
        NONE(" IGNORE INDEX (idx_delivery_requests, idx_delivery_driver_shipments)"),
        DRIVER_ONLY(" IGNORE INDEX (idx_delivery_requests)"),
        REQUESTS_ONLY(" IGNORE INDEX (idx_delivery_driver_shipments)"),
        BOTH("");

        private final String hint;

        Mode(String hint) {
            this.hint = hint;
        }
    }

    @Test
    @Timeout(180)
    void compareEachIndexAndBothIncludingUnassignedCancellationHistory() throws Exception {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new CapturingJdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        // Compare the additional driver index locally; production V10 deliberately adds only the request index.
        jdbc.execute("CREATE INDEX idx_delivery_driver_shipments ON delivery(driver_id, accepted_at, id)");
        var repository = new JdbcDeliveryQueryRepository(jdbc);
        var samples = new ArrayList<Map<String, Object>>();
        Path output = Path.of(System.getProperty("delivery.index.output")).toAbsolutePath();
        Files.createDirectories(output);
        var mapper = new ObjectMapper();
        try {
            for (String scenario : List.of("assigned-history", "cancelled-history")) {
                seed(jdbc, scenario);
                jdbc.execute("ANALYZE TABLE delivery");
                jdbc.mode = Mode.NONE;
                var expectedRequests = repository.findAvailableRequests();
                var expectedShipments = repository.findShipmentsByDriverId(new DriverId(7L));
                assertThat(expectedRequests).hasSize(500);
                assertThat(expectedShipments).hasSize(50);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery", Integer.class)).isEqualTo(50_000);
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM delivery WHERE status='CANCELLED' AND driver_id IS NULL", Integer.class))
                        .isEqualTo(scenario.equals("cancelled-history") ? 24_500 : 0);

                // Rotate all four modes to avoid always measuring the same mode first.
                for (int run = -WARMUP; run < MEASUREMENTS; run++) {
                    var modes = new ArrayList<>(Arrays.asList(Mode.values()));
                    java.util.Collections.rotate(modes, run + WARMUP);
                    for (Mode mode : modes) {
                        jdbc.mode = mode;
                        assertThat(repository.findAvailableRequests()).containsExactlyElementsOf(expectedRequests);
                        var request = capturePlan(jdbc, scenario, run, mode, "requests");
                        assertThat(repository.findShipmentsByDriverId(new DriverId(7L)))
                                .containsExactlyElementsOf(expectedShipments);
                        var shipment = capturePlan(jdbc, scenario, run, mode, "shipments");
                        samples.add(request);
                        samples.add(shipment);
                    }
                }
                System.out.println("DELIVERY_INDEX_COMPARISON scenario=" + scenario
                        + " total=50000 requested=500 driver7=50 all_results_equal=true");
            }
        } finally {
            Files.writeString(output.resolve("samples.json"), mapper.writeValueAsString(samples));
            Files.writeString(output.resolve("metadata.json"), mapper.writeValueAsString(Map.of(
                    "mysql", jdbc.queryForObject("SELECT VERSION()", String.class),
                    "image", MYSQL.getDockerImageName(), "warmup_per_mode_and_query", WARMUP,
                    "measurements_per_mode_and_query", MEASUREMENTS,
                    "execution", "Sequential local JDBC repository calls plus EXPLAIN ANALYZE; no HTTP load",
                    "comparison", "Ignore both, either one, or no added index; data and result order unchanged")));
        }
    }

    private Map<String, Object> capturePlan(CapturingJdbcTemplate jdbc, String scenario, int run,
                                           Mode mode, String query) {
        String sql = jdbc.lastSql;
        Object[] arguments = jdbc.lastArguments;
        String plan = jdbc.queryForObject("EXPLAIN ANALYZE " + sql, String.class, arguments);
        var result = new LinkedHashMap<String, Object>();
        result.put("scenario", scenario);
        result.put("phase", run < 0 ? "warmup" : "measurement");
        result.put("run", run < 0 ? run + WARMUP + 1 : run + 1);
        result.put("mode", mode.name());
        result.put("query", query);
        result.put("sql", sql);
        result.put("arguments", List.of(arguments));
        result.put("plan", plan);
        return result;
    }

    private void seed(JdbcTemplate jdbc, String scenario) {
        jdbc.execute("TRUNCATE TABLE delivery");
        String cancelledCondition = scenario.equals("cancelled-history") ? "MOD(n, 2) = 0" : "FALSE";
        jdbc.execute("""
                INSERT INTO delivery
                    (id, order_id, driver_id, item_name, category, pickup_address, delivery_address,
                     pickup_phone_number, delivery_phone_number, estimated_fee, status,
                     requested_at, accepted_at, picked_up_at, delivered_at)
                WITH digit AS (
                    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
                    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
                    UNION ALL SELECT 8 UNION ALL SELECT 9
                ), numbers AS (
                    SELECT a.n + 10*b.n + 100*c.n + 1000*d.n + 10000*e.n + 1 AS n
                    FROM digit a CROSS JOIN digit b CROSS JOIN digit c
                    CROSS JOIN digit d CROSS JOIN digit e WHERE e.n < 5
                ), fixture AS (
                    SELECT n, CASE WHEN MOD(n, 100) = 0 THEN 'REQUESTED'
                         WHEN %s THEN 'CANCELLED'
                         WHEN MOD(FLOOR((n-1)/1000), 3) = 0 THEN 'ACCEPTED'
                         WHEN MOD(FLOOR((n-1)/1000), 3) = 1 THEN 'PICKED_UP'
                         ELSE 'DELIVERED' END AS fixture_status
                    FROM numbers
                )
                SELECT n, n, IF(fixture_status IN ('REQUESTED','CANCELLED'), NULL, MOD(n-1,1000)+1),
                       'Synthetic desk', 'DESK', 'Synthetic pickup', 'Synthetic destination',
                       '010-0000-0000', '010-0000-0000', 10000, fixture_status,
                       TIMESTAMP('2026-10-01') + INTERVAL FLOOR(n/10) SECOND,
                       IF(fixture_status IN ('REQUESTED','CANCELLED'), NULL,
                          TIMESTAMP('2026-10-01') + INTERVAL (FLOOR(n/10)+60) SECOND),
                       IF(fixture_status IN ('PICKED_UP','DELIVERED'),
                          TIMESTAMP('2026-10-01') + INTERVAL (FLOOR(n/10)+120) SECOND, NULL),
                       IF(fixture_status='DELIVERED',
                          TIMESTAMP('2026-10-01') + INTERVAL (FLOOR(n/10)+180) SECOND, NULL)
                FROM fixture
                """.formatted(cancelledCondition));
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private Mode mode = Mode.NONE;
        private String lastSql;
        private Object[] lastArguments;

        CapturingJdbcTemplate(DriverManagerDataSource dataSource) {
            super(dataSource);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            return super.query(rewrite(sql, new Object[0]), rowMapper);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return super.query(rewrite(sql, args), rowMapper, args);
        }

        private String rewrite(String sql, Object[] arguments) {
            if (!sql.stripLeading().startsWith("SELECT id, item_name")) {
                return sql;
            }
            lastSql = sql.replace("FROM delivery", "FROM delivery" + mode.hint);
            lastArguments = arguments;
            return lastSql;
        }
    }
}
