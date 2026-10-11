package setty.performance;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.responseTimeInMillis;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.RawFileBodyPart;
import static io.gatling.javaapi.http.HttpDsl.StringBodyPart;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ListingRegisterSimulation extends Simulation {

    private static final String DEV_BASE_URL = "https://www.setty.cloud";
    private static final String REGISTER_PATH = "/dev/api/listings";
    private static final String WARMUP = "register-warmup";
    private static final String MEASUREMENT = "register-measurement";
    private static final String BURST = "register-burst";
    private static final int WARMUP_REQUESTS = 5;

    private final String baseUrl = System.getenv().getOrDefault("NOTIFY_BASE_URL", DEV_BASE_URL);
    private final String sellerToken = requireEnv("SELLER_TOKEN");
    private final String mode = System.getenv().getOrDefault("REGISTER_MODE", "single");
    private final int repeats = Integer.parseInt(System.getenv().getOrDefault("REGISTER_REPEATS", "30"));
    private final int burstUsers = Integer.parseInt(System.getenv().getOrDefault("BURST_USERS", "10"));
    private final int pauseSeconds = Integer.parseInt(System.getenv().getOrDefault("REGISTER_PAUSE_SECONDS", "3"));
    private final String subscribers = System.getenv().getOrDefault("SUBSCRIBERS", "unknown");
    private final String structure = System.getenv().getOrDefault("STRUCTURE", "unknown");
    private final StringBuilder samples = new StringBuilder(
            "structure,subscribers,target,phase,request_number,completed_at,response_time_ms,listing_id,failed,request_id\n");
    private final AtomicInteger requestNumber = new AtomicInteger();

    public ListingRegisterSimulation() {
        if (!Set.of("single", "burst").contains(mode)) {
            throw new IllegalArgumentException("REGISTER_MODE must be single or burst");
        }
        if (!DEV_BASE_URL.equals(baseUrl)
                && !baseUrl.matches("http://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}")) {
            throw new IllegalArgumentException("NOTIFY_BASE_URL must be the dev URL or a local verification server");
        }

        var protocol = http.baseUrl(baseUrl)
                .acceptHeader("application/json")
                .authorizationHeader("Bearer " + sellerToken)
                .disableCaching()
                .disableFollowRedirect()
                .disableWarmUp();

        if (mode.equals("single")) {
            ScenarioBuilder single = scenario("매물 등록 반복")
                    .repeat(WARMUP_REQUESTS).on(register(WARMUP))
                    .repeat(repeats).on(register(MEASUREMENT));
            setUp(single.injectOpen(atOnceUsers(1)))
                    .protocols(protocol)
                    .assertions(
                            global().failedRequests().count().is(0L),
                            details(MEASUREMENT).allRequests().count().is((long) repeats));
        } else {
            ScenarioBuilder burst = scenario("매물 동시 등록").exec(register(BURST));
            setUp(burst.injectOpen(atOnceUsers(burstUsers)))
                    .protocols(protocol)
                    .assertions(
                            global().failedRequests().count().is(0L),
                            details(BURST).allRequests().count().is((long) burstUsers));
        }
    }

    private ChainBuilder register(String phase) {
        return exec(session -> session.removeAll("responseTimeMs", "listingId", "requestId"))
                .exec(session -> session.set("requestNumber", requestNumber.incrementAndGet()))
                .exec(http(phase)
                        .post(path())
                        .bodyPart(StringBodyPart("request", session -> requestJson(session.getInt("requestNumber")))
                                .contentType("application/json"))
                        .bodyPart(RawFileBodyPart("images", "notification-tiny.png")
                                .fileName("notification-tiny.png")
                                .contentType("image/png"))
                        .check(
                                status().is(201),
                                jsonPath("$.listingId").saveAs("listingId"),
                                responseTimeInMillis().saveAs("responseTimeMs"),
                                header("X-Request-Id").optional().saveAs("requestId")))
                .exec(session -> {
                    synchronized (samples) {
                        samples.append(structure).append(',').append(subscribers).append(',')
                                .append(baseUrl).append(',').append(phase).append(',')
                                .append(session.getInt("requestNumber")).append(',')
                                .append(Instant.now()).append(',')
                                .append(session.contains("responseTimeMs") ? session.getInt("responseTimeMs") : "")
                                .append(',')
                                .append(session.contains("listingId") ? session.getString("listingId") : "")
                                .append(',').append(session.isFailed()).append(',')
                                .append(csvCell(session.contains("requestId") ? session.getString("requestId") : ""))
                                .append('\n');
                    }
                    return session;
                })
                .exitHereIfFailed()
                .pause(pauseSeconds);
    }

    private String path() {
        return DEV_BASE_URL.equals(baseUrl) ? REGISTER_PATH : "/api/listings";
    }

    private static String requestJson(int number) {
        return """
                {"title":"[S3-N001] s3-notify desk %06d","description":"Synthetic listing for the Sprint 3 notification experiment. Not for purchase.","price":50000,"category":"DESK","conditionGrade":"A","dimensions":{"widthCm":80,"depthCm":50,"heightCm":70}}
                """.formatted(number).strip();
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value;
    }

    @Override
    public void before() {
        System.out.printf(
                "Listing register experiment: target=%s, structure=%s, subscribers=%s, mode=%s, repeats=%d, burstUsers=%d, pause=%ds%n",
                baseUrl, structure, subscribers, mode, repeats, burstUsers, pauseSeconds);
    }

    private static String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void after() {
        try {
            Path directory = Path.of("build", "reports", "listing-register");
            Files.createDirectories(directory);
            Path file = directory.resolve("register-%s-%s-%s-%d.csv".formatted(structure, subscribers, mode, System.currentTimeMillis()));
            Files.writeString(file, samples.toString());
            System.out.println("Listing register samples: " + file.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
