package setty.performance;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.bodyLength;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.responseTimeInMillis;
import static io.gatling.javaapi.core.CoreDsl.scenario;
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
import java.util.Set;

public class ListingCountSimulation extends Simulation {

    private static final String DEV_BASE_URL = "https://www.setty.cloud";
    private static final String WARMUP = "listings-warmup";
    private static final String MEASUREMENT = "listings-measurement";
    private static final int WARMUP_REQUESTS = 20;
    private static final int MEASUREMENT_REQUESTS = 100;

    private final int expectedItems = Integer.parseInt(
            System.getenv().getOrDefault("LISTING_COUNT", "100"));
    private final String baseUrl = System.getenv().getOrDefault("LISTING_BASE_URL", DEV_BASE_URL);
    // 가상 사용자 1명이 순차적으로 기록하고, 파일 쓰기는 측정 종료 후 한 번만 한다.
    private final StringBuilder samples = new StringBuilder(
            "expected_items,target,phase,request_number,completed_at,response_time_ms,response_body_bytes,failed\n");
    private int requestNumber;

    public ListingCountSimulation() {
        if (!Set.of(100, 1000, 5000).contains(expectedItems)) {
            throw new IllegalArgumentException("LISTING_COUNT must be 100, 1000, or 5000");
        }
        if (!DEV_BASE_URL.equals(baseUrl)
                && !baseUrl.matches("http://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}")) {
            throw new IllegalArgumentException("LISTING_BASE_URL must be the dev URL or a local verification server");
        }

        var protocol = http.baseUrl(baseUrl)
                .acceptHeader("application/json")
                .disableCaching()
                .disableFollowRedirect()
                .disableWarmUp(); // Gatling 자체의 외부 URL 워밍업 대신 아래 20회로 준비한다.

        var listingScenario = scenario("매물 수 증가 탐색")
                .repeat(WARMUP_REQUESTS).on(listingRequest(WARMUP))
                .repeat(MEASUREMENT_REQUESTS).on(listingRequest(MEASUREMENT));

        setUp(listingScenario.injectOpen(atOnceUsers(1)))
                .protocols(protocol)
                .assertions(
                        global().failedRequests().count().is(0L),
                        details(WARMUP).allRequests().count().is((long) WARMUP_REQUESTS),
                        details(MEASUREMENT).allRequests().count().is((long) MEASUREMENT_REQUESTS));
    }

    private ChainBuilder listingRequest(String phase) {
        return exec(session -> session.removeAll("responseTimeMs", "responseBodyBytes"))
                .exec(http(phase)
                        .get("/dev/api/listings")
                        .check(
                                status().is(200),
                                jsonPath("$.items").ofList().transform(List::size).is(expectedItems),
                                responseTimeInMillis().saveAs("responseTimeMs"),
                                bodyLength().saveAs("responseBodyBytes")))
                .exec(session -> {
                    samples.append(expectedItems).append(',').append(baseUrl).append(',')
                            .append(phase).append(',').append(++requestNumber).append(',')
                            .append(Instant.now()).append(',')
                            .append(session.contains("responseTimeMs") ? session.getInt("responseTimeMs") : "")
                            .append(',')
                            .append(session.contains("responseBodyBytes") ? session.getInt("responseBodyBytes") : "")
                            .append(',').append(session.isFailed()).append('\n');
                    return session;
                })
                .exitHereIfFailed()
                .pause(1); // 응답 완료 후 대기하므로 고정 1 RPS가 아니다.
    }

    @Override
    public void before() {
        System.out.printf(
                "Listing count experiment: target=%s, items=%d, users=1, warmup=%d, measurement=%d, pause=1s%n",
                baseUrl, expectedItems, WARMUP_REQUESTS, MEASUREMENT_REQUESTS);
    }

    @Override
    public void after() {
        try {
            Path directory = Path.of("build", "reports", "listing-count");
            Files.createDirectories(directory);
            Path output = Files.createTempFile(directory, "listings-" + expectedItems + "-", ".csv");
            Files.writeString(output, samples.toString());
            System.out.println("Listing count samples: " + output.toAbsolutePath());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save listing count samples", exception);
        }
    }
}
