package setty.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * DB가 필요한 통합 테스트의 공통 부모. 테스트 JVM 하나에서 MySQL 컨테이너를 한 번만 띄워 모든 테스트가 공유한다.
 *
 * <p>클래스마다 컨테이너를 띄우면 클래스가 끝날 때 컨테이너가 내려가는데, 캐시된 Spring 컨텍스트는 JVM 종료까지 남는다.
 * 종료 시 컨텍스트가 닫히면서 이미 없는 DB에 접속하려고 컨텍스트마다 30초씩 기다리므로, 컨테이너는 JVM 종료까지 유지한다.
 * 컨테이너 정리는 Testcontainers(Ryuk)가 맡는다.
 */
public abstract class MySqlIntegrationTestSupport {

    // 개발 DB(docker-compose.yml)와 같은 버전을 쓴다.
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    static {
        MYSQL.start();
    }

    // 컨텍스트가 캐시에 남아 공유 DB를 계속 보므로, 남은 컨텍스트의 만료·판매 완료 스케줄러가 다른 테스트의 주문을 건드리지 않게 막는다.
    @DynamicPropertySource
    static void disableOrderScans(final DynamicPropertyRegistry registry) {
        registry.add("setty.order.pending-expiration.scan-interval", () -> "PT24H");
        registry.add("setty.order.completion.scan-interval", () -> "PT24H");
    }
}
