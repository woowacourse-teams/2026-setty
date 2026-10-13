package setty.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
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

/** Independent tenfold changes of future and due populations; these are capacity probes, not traffic forecasts. */
@Tag("local-pending-expiration")
@SpringBootTest(properties = {
        "management.otlp.metrics.export.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "setty.performance.PendingExpirationBaselineTest$SqlCapture"
})
public class PendingExpirationScaleTest extends MySqlIntegrationTestSupport {
    private static final long SELLER = 920001;
    private static final long BUYER = 920002;
    private static final int HISTORY = 1_000;
    private static final Instant REFERENCE = Instant.parse("2026-10-12T06:00:00Z");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private OrderRepository orders;
    @Autowired private PendingOrderExpirationScheduler scheduler;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private Clock clock;

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void separateFuturePopulationFromDueBatchCost() throws Exception {
        Path output = Path.of(System.getProperty("expiration.benchmark.output")).toAbsolutePath();
        Files.createDirectories(output);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("setty_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings", Integer.class)).isZero();
        when(clock.instant()).thenReturn(REFERENCE);
        var samples = new ArrayList<Map<String, Object>>();
        var plans = new ArrayList<Map<String, Object>>();
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        try {
            seed(30, 1_000);
            scheduler.expirePendingOrders();
            for (int future : List.of(1_000, 10_000)) {
                seed(30, future);
                jdbc.execute("ANALYZE TABLE orders");
                plans.add(Map.of("due", 30, "future", future, "plan", jdbc.queryForList("""
                        EXPLAIN ANALYZE SELECT id FROM orders
                        WHERE order_status='PENDING' AND pending_expires_at<='2026-10-12 06:00:00'
                        ORDER BY pending_expires_at ASC
                        """)));
                for (int round = 0; round < 15; round++) {
                    long start = System.nanoTime();
                    var ids = orders.findExpiredOrderIds(OrderStatus.PENDING, REFERENCE);
                    double elapsed = millis(start);
                    assertThat(ids).hasSize(30);
                    if (round >= 3) samples.add(Map.of("scenario", "candidate_query", "future", future,
                            "due", 30, "round", round - 2, "query_ms", elapsed));
                }
            }
            for (int round = 1; round <= 3; round++) {
                var sizes = round % 2 == 0 ? List.of(10_000, 1_000) : List.of(1_000, 10_000);
                for (int due : sizes) {
                    seed(due, 1_000);
                    stats.clear();
                    PendingExpirationBaselineTest.SqlCapture.count.set(0);
                    long start = System.nanoTime();
                    scheduler.expirePendingOrders();
                    double elapsed = millis(start);
                    var sample = new LinkedHashMap<String, Object>();
                    sample.put("scenario", "due_batch");
                    sample.put("due", due);
                    sample.put("future", 1_000);
                    sample.put("round", round);
                    sample.put("job_ms", elapsed);
                    sample.put("hibernate_sql", PendingExpirationBaselineTest.SqlCapture.count.get());
                    sample.put("hibernate_transactions", stats.getTransactionCount());
                    samples.add(sample);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id=? AND id<=? AND order_status='EXPIRED'",
                            Integer.class, BUYER, due)).isEqualTo(due);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE seller_id=? AND id<=? AND has_purchase_request=FALSE",
                            Integer.class, SELLER, due)).isEqualTo(due);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id=? AND order_status='PENDING'",
                            Integer.class, BUYER)).isEqualTo(1_000);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM listings WHERE seller_id=? AND id>? AND id<=? AND has_purchase_request=TRUE",
                            Integer.class, SELLER, due, due + 1_000)).isEqualTo(1_000);
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id=?", Integer.class, BUYER))
                            .isEqualTo(due + 1_000 + HISTORY);
                    System.out.println("PENDING_EXPIRATION_SCALE " + mapper.writeValueAsString(sample));
                }
            }
        } finally {
            Files.writeString(output.resolve("scale-samples.json"), mapper.writeValueAsString(samples));
            Files.writeString(output.resolve("scale-plans.json"), mapper.writeValueAsString(plans));
            cleanup();
        }
        Files.writeString(output.resolve("scale-metadata.json"), mapper.writeValueAsString(Map.of(
                "reference_time", REFERENCE.toString(), "java", System.getProperty("java.version"),
                "mysql", jdbc.queryForObject("SELECT VERSION()", String.class),
                "history", HISTORY,
                "traffic_basis", "Tenfold sensitivity probes of independent populations, not expected service traffic",
                "method", "Unchanged Spring scheduler invoked directly, real temporary MySQL; excludes HTTP, real six-minute aging and scheduling waits")));
    }

    private void seed(int due, int future) {
        cleanup();
        jdbc.update("""
                INSERT INTO members(id,login_id,password,role,phone_number,address)
                VALUES (?, 'scale-seller', REPEAT('!',60), 'MEMBER', '000-0000-0000', 'LOCAL EXPERIMENT'),
                       (?, 'scale-buyer', REPEAT('!',60), 'MEMBER', '000-0000-0000', 'LOCAL EXPERIMENT')
                """, SELLER, BUYER);
        var listings = new ArrayList<Object[]>();
        var orderRows = new ArrayList<Object[]>();
        for (int id = 1; id <= due + future + HISTORY; id++) {
            boolean closed = id > due + future;
            listings.add(new Object[]{id, SELLER, !closed});
            Instant deadline = id <= due ? REFERENCE.minusSeconds(1)
                    : closed ? REFERENCE.minusSeconds(120) : REFERENCE.plusSeconds(300);
            orderRows.add(new Object[]{id, id, BUYER, closed ? "EXPIRED" : "PENDING",
                    deadline.toString().replace('T', ' ').replace("Z", "")});
        }
        jdbc.batchUpdate("""
                INSERT INTO listings(id,seller_id,title,description,price,delivery_fee,category,condition_grade,
                    width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at)
                VALUES (?,?,'[LOCAL] Scale fixture','Synthetic data',50000,10000,'DESK','A',
                    80,50,70,'AVAILABLE',?,'2026-10-12 05:00:00','2026-10-12 05:00:00')
                """, listings);
        jdbc.batchUpdate("""
                INSERT INTO orders(id,listing_id,buyer_id,delivery_status,order_status,pending_expires_at)
                VALUES (?,?,?,'PENDING',?,?)
                """, orderRows);
        assertThat(orders.findExpiredOrderIds(OrderStatus.PENDING, REFERENCE)).hasSize(due);
    }

    private void cleanup() {
        jdbc.update("DELETE FROM orders WHERE buyer_id=?", BUYER);
        jdbc.update("DELETE FROM listings WHERE seller_id=?", SELLER);
        jdbc.update("DELETE FROM members WHERE id IN (?,?)", SELLER, BUYER);
    }

    private static double millis(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
}
