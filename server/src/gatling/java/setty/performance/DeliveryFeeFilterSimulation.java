package setty.performance;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.bodyLength;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.doIf;
import static io.gatling.javaapi.core.CoreDsl.doIfOrElse;
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
import java.util.concurrent.atomic.AtomicInteger;

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
    // 1이면 첫 페이지만, 2 이상이면 nextCursor로 그 페이지 수까지 이어 받는다(다음 페이지가 없으면 거기서 멈춘다)
    private final int pages = Integer.parseInt(System.getenv().getOrDefault("PAGES", "1"));
    private final int users = Integer.parseInt(System.getenv().getOrDefault("USERS", "1"));
    private final int pauseSeconds = Integer.parseInt(System.getenv().getOrDefault("PAUSE_SECONDS", "1"));
    private final StringBuilder samples = new StringBuilder(
            "buyer_dong,max_delivery_fee,page_size,users,target,phase,page,request_number,completed_at,response_time_ms,"
                    + "items,has_next,response_body_bytes,failed,request_id\n");
    private final AtomicInteger requestNumber = new AtomicInteger();

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
        if (pages < 1 || pages > 20) {
            throw new IllegalArgumentException("PAGES must be between 1 and 20");
        }
        if (users < 1 || users > 100) {
            throw new IllegalArgumentException("USERS must be between 1 and 100");
        }
        if (pauseSeconds < 0 || pauseSeconds > 10) {
            throw new IllegalArgumentException("PAUSE_SECONDS must be between 0 and 10");
        }

        var protocol = http.baseUrl(baseUrl)
                .acceptHeader("application/json")
                .authorizationHeader("Bearer " + buyerToken)
                .disableCaching()
                .disableFollowRedirect()
                .disableWarmUp();

        var filterScenario = scenario("배송비 필터")
                .exec(http(SETUP)
                        .put(apiPath("/auth/me/dong"))
                        .header("Content-Type", "application/json")
                        .body(StringBody("{\"dongCode\":\"" + buyerDong + "\"}"))
                        .check(status().is(200), jsonPath("$.dongCode").is(buyerDong)))
                .exitHereIfFailed()
                .repeat(WARMUP_REQUESTS).on(pageWalk(WARMUP))
                .repeat(MEASUREMENT_REQUESTS).on(pageWalk(MEASUREMENT));

        // 첫 페이지 요청 이름은 phase 그대로라 사용자마다 정확히 반복 횟수만큼 나온다. 뒤쪽 페이지는 "-p2" 같은 이름으로 따로 집계한다.
        setUp(filterScenario.injectOpen(atOnceUsers(users)))
                .protocols(protocol)
                .assertions(
                        global().failedRequests().count().is(0L),
                        details(WARMUP).allRequests().count().is((long) WARMUP_REQUESTS * users),
                        details(MEASUREMENT).allRequests().count().is((long) MEASUREMENT_REQUESTS * users));
    }

    private ChainBuilder pageWalk(String phase) {
        return exec(session -> session.remove("nextCursor"))
                .repeat(pages, "pageIndex").on(
                        doIf(session -> session.getInt("pageIndex") == 0 || session.contains("nextCursor"))
                                .then(doIfOrElse(session -> session.getInt("pageIndex") == 0)
                                        .then(pageRequest(phase, false))
                                        .orElse(pageRequest(phase, true))))
                .exitHereIfFailed()
                .pause(pauseSeconds);
    }

    private ChainBuilder pageRequest(String phase, boolean withCursor) {
        var request = http(session -> requestName(phase, session.getInt("pageIndex") + 1))
                .get(apiPath("/listings"))
                .queryParam("size", pageSize);
        if (!maxDeliveryFee.equals("none")) {
            request = request.queryParam("maxDeliveryFee", maxDeliveryFee);
        }
        if (withCursor) {
            request = request.queryParam("cursor", session -> session.getString("cursor"));
        }
        // 이번 요청에 쓸 커서를 옮겨 두고 지난 응답 값을 지운다. 지우지 않으면 마지막 페이지에서도 지난 커서가 남는다.
        return exec(session -> (session.contains("nextCursor") ? session.set("cursor", session.getString("nextCursor")) : session)
                        .removeAll("responseTimeMs", "items", "nextCursor", "responseBodyBytes", "requestId"))
                .exec(request.check(
                        status().is(200),
                        jsonPath("$.items").ofList().transform(List::size).lte(pageSize).saveAs("items"),
                        jsonPath("$.nextCursor").optional().saveAs("nextCursor"),
                        responseTimeInMillis().saveAs("responseTimeMs"),
                        bodyLength().saveAs("responseBodyBytes"),
                        header("X-Request-Id").optional().saveAs("requestId")))
                .exec(session -> {
                    String row = String.join(",",
                            buyerDong, maxDeliveryFee, String.valueOf(pageSize), String.valueOf(users), baseUrl, phase,
                            String.valueOf(session.getInt("pageIndex") + 1), String.valueOf(requestNumber.incrementAndGet()),
                            Instant.now().toString(),
                            session.contains("responseTimeMs") ? String.valueOf(session.getInt("responseTimeMs")) : "",
                            session.contains("items") ? String.valueOf(session.getInt("items")) : "",
                            String.valueOf(session.contains("nextCursor")),
                            session.contains("responseBodyBytes") ? String.valueOf(session.getInt("responseBodyBytes")) : "",
                            String.valueOf(session.isFailed()),
                            csvCell(session.contains("requestId") ? session.getString("requestId") : ""));
                    synchronized (samples) {
                        samples.append(row).append('\n');
                    }
                    return session;
                })
                .exitHereIfFailed();
    }

    private static String requestName(String phase, int page) {
        return page == 1 ? phase : phase + "-p" + page;
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
                "Delivery fee filter experiment: target=%s, buyerDong=%s, maxDeliveryFee=%s, size=%d, pages=%d, users=%d, warmup=%d, measurement=%d, pause=%ds%n",
                baseUrl, buyerDong, maxDeliveryFee, pageSize, pages, users, WARMUP_REQUESTS, MEASUREMENT_REQUESTS, pauseSeconds);
    }

    @Override
    public void after() {
        try {
            Path directory = Path.of("build", "reports", "delivery-fee-filter");
            Files.createDirectories(directory);
            Path output = Files.createTempFile(directory,
                    "filter-%s-%s-p%d-u%d-".formatted(buyerDong, maxDeliveryFee, pages, users), ".csv");
            synchronized (samples) {
                Files.writeString(output, samples.toString());
            }
            System.out.println("Delivery fee filter samples: " + output.toAbsolutePath());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save delivery fee filter samples", exception);
        }
    }
}
