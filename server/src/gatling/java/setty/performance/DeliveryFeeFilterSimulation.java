package setty.performance;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.bodyLength;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.responseTimeInMillis;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Simulation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class DeliveryFeeFilterSimulation extends Simulation {

    private static final String DEV_BASE_URL = "https://www.setty.cloud";
    private static final String SETUP = "filter-setup";
    private static final String WARMUP = "filter-warmup";
    private static final String MEASUREMENT = "filter-measurement";
    private static final int WARMUP_REQUESTS = 20;
    private static final int MEASUREMENT_REQUESTS = 100;

    private final String baseUrl = System.getenv().getOrDefault("LISTING_BASE_URL", DEV_BASE_URL);
    private final String buyerToken = requireEnv("BUYER_TOKEN");
    private final String buyerDong = System.getenv().getOrDefault("BUYER_DONG", "1111053000");
    // "none"이면 같은 페이지 크기로 필터 없이 불러 비교 기준을 잰다
    private final String maxDeliveryFee = System.getenv().getOrDefault("MAX_DELIVERY_FEE", "20000");
    private final int pageSize = Integer.parseInt(System.getenv().getOrDefault("PAGE_SIZE", "20"));
    private final StringBuilder samples = new StringBuilder(
            "buyer_dong,max_delivery_fee,page_size,target,phase,request_number,completed_at,response_time_ms,"
                    + "items,has_next,response_body_bytes,failed,request_id\n");
    private int requestNumber;

    public DeliveryFeeFilterSimulation() {
        if (!DEV_BASE_URL.equals(baseUrl)
                && !baseUrl.matches("http://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}")) {
            throw new IllegalArgumentException("LISTING_BASE_URL must be the dev URL or a local verification server");
        }
        if (!buyerDong.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("BUYER_DONG must be a 10-digit Seoul dong code");
        }
        if (!maxDeliveryFee.equals("none") && !maxDeliveryFee.matches("[0-9]{1,7}")) {
            throw new IllegalArgumentException("MAX_DELIVERY_FEE must be a number or none");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("PAGE_SIZE must be between 1 and 100");
        }

        var protocol = http.baseUrl(baseUrl)
                .acceptHeader("application/json")
                .authorizationHeader("Bearer " + buyerToken)
                .disableCaching()
                .disableFollowRedirect()
                .disableWarmUp();

        var filterScenario = scenario("배송비 필터 첫 페이지")
                .exec(http(SETUP)
                        .put(apiPath("/auth/me/dong"))
                        .header("Content-Type", "application/json")
                        .body(StringBody("{\"dongCode\":\"" + buyerDong + "\"}"))
                        .check(status().is(200), jsonPath("$.dongCode").is(buyerDong)))
                .exitHereIfFailed()
                .repeat(WARMUP_REQUESTS).on(filterRequest(WARMUP))
                .repeat(MEASUREMENT_REQUESTS).on(filterRequest(MEASUREMENT));

        setUp(filterScenario.injectOpen(atOnceUsers(1)))
                .protocols(protocol)
                .assertions(
                        global().failedRequests().count().is(0L),
                        details(WARMUP).allRequests().count().is((long) WARMUP_REQUESTS),
                        details(MEASUREMENT).allRequests().count().is((long) MEASUREMENT_REQUESTS));
    }

    private ChainBuilder filterRequest(String phase) {
        var request = http(phase)
                .get(apiPath("/listings"))
                .queryParam("size", pageSize);
        if (!maxDeliveryFee.equals("none")) {
            request = request.queryParam("maxDeliveryFee", maxDeliveryFee);
        }
        return exec(session -> session.removeAll("responseTimeMs", "items", "nextCursor", "responseBodyBytes", "requestId"))
                .exec(request.check(
                        status().is(200),
                        jsonPath("$.items").ofList().transform(List::size).lte(pageSize).saveAs("items"),
                        jsonPath("$.nextCursor").optional().saveAs("nextCursor"),
                        responseTimeInMillis().saveAs("responseTimeMs"),
                        bodyLength().saveAs("responseBodyBytes"),
                        header("X-Request-Id").optional().saveAs("requestId")))
                .exec(session -> {
                    samples.append(buyerDong).append(',').append(maxDeliveryFee).append(',').append(pageSize).append(',')
                            .append(baseUrl).append(',').append(phase).append(',').append(++requestNumber).append(',')
                            .append(Instant.now()).append(',')
                            .append(session.contains("responseTimeMs") ? session.getInt("responseTimeMs") : "").append(',')
                            .append(session.contains("items") ? session.getInt("items") : "").append(',')
                            .append(session.contains("nextCursor")).append(',')
                            .append(session.contains("responseBodyBytes") ? session.getInt("responseBodyBytes") : "")
                            .append(',').append(session.isFailed()).append(',')
                            .append(csvCell(session.contains("requestId") ? session.getString("requestId") : ""))
                            .append('\n');
                    return session;
                })
                .exitHereIfFailed()
                .pause(1);
    }

    private String apiPath(String path) {
        return (DEV_BASE_URL.equals(baseUrl) ? "/dev/api" : "/api") + path;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value;
    }

    private static String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void before() {
        System.out.printf(
                "Delivery fee filter experiment: target=%s, buyerDong=%s, maxDeliveryFee=%s, size=%d, warmup=%d, measurement=%d, pause=1s%n",
                baseUrl, buyerDong, maxDeliveryFee, pageSize, WARMUP_REQUESTS, MEASUREMENT_REQUESTS);
    }

    @Override
    public void after() {
        try {
            Path directory = Path.of("build", "reports", "delivery-fee-filter");
            Files.createDirectories(directory);
            Path output = Files.createTempFile(directory, "filter-%s-%s-".formatted(buyerDong, maxDeliveryFee), ".csv");
            Files.writeString(output, samples.toString());
            System.out.println("Delivery fee filter samples: " + output.toAbsolutePath());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save delivery fee filter samples", exception);
        }
    }
}
