package setty.performance;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

public class ListingSmokeSimulation extends Simulation {

    public ListingSmokeSimulation() {
        HttpProtocolBuilder httpProtocol = http
                .baseUrl("https://www.setty.cloud")
                .acceptHeader("application/json");

        ScenarioBuilder listingScenario = scenario("매물 목록 연결 확인")
                .exec(
                        http("매물 목록 조회")
                                .get("/dev/api/listings")
                                .check(
                                        status().is(200),
                                        jsonPath("$.items").exists()
                                )
                );

        setUp(
                listingScenario.injectOpen(atOnceUsers(1))
        )
                .protocols(httpProtocol)
                .assertions(global().failedRequests().count().is(0L));
    }
}
