package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import setty.platform.OrderSupportReader;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;

/** Runs only against the disposable MySQL test container, never the configured application DB. */
@SpringBootTest(properties={"setty.order.pending-expiration.scan-interval=PT24H","setty.order.completion.scan-interval=PT24H"})
class SupportEvaluationFixtureTest {
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11")
            .withDatabaseName("setty_test").withUsername("setty_test").withPassword("setty_test");
    static { MYSQL.start(); }
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderSupportReader orders;
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM delivery_order_decision WHERE order_id IN (SELECT id FROM orders WHERE buyer_id IN (920001,920003))");
        jdbc.update("DELETE FROM delivery WHERE order_id IN (SELECT id FROM orders WHERE buyer_id IN (920001,920003))");
        jdbc.update("DELETE FROM orders WHERE buyer_id IN (920001,920003)");
        jdbc.update("DELETE FROM listings WHERE seller_id=920002");
        jdbc.update("DELETE FROM members WHERE id IN (920001,920002,920003)");
    }
    @Test void safetyGuardRejectsNonemptyDatabaseBeforeAnyFixtureInsertion() {
        jdbc.update("INSERT INTO members(id,login_id,password,role,phone_number,address) VALUES(920003,'existing-row','encoded','MEMBER','010-0000-0000','existing')");
        assertThatThrownBy(()->new ResourceDatabasePopulator(new ClassPathResource("aisupport/eval-fixture.sql")).execute(dataSource))
                .isInstanceOf(org.springframework.jdbc.datasource.init.ScriptException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Long.class)).isZero();
    }
    @Test void publishedSyntheticFixtureLoadsAndMatchesEvaluationFacts() {
        new ResourceDatabasePopulator(new ClassPathResource("aisupport/eval-fixture.sql")).execute(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery d LEFT JOIN delivery_order_decision r ON r.order_id=d.order_id WHERE r.order_id IS NULL", Long.class))
                .as("Every seeded delivery must have the registration decision that real registration creates")
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE buyer_id IN(920001,920003)",Long.class)).isEqualTo(28);
        var duplicates=orders.findMyOrders(920001L,OrderSupportReader.Search.browse(null, "평가 중복소파", null, null, null, 5));
        assertThat(duplicates.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(930002L,930001L);
        assertThat(orders.getContext(920001L,930003L).reasonCode()).isEqualTo("BEFORE_DRIVER_ACCEPTANCE");
        assertThat(orders.getContext(920001L,930003L).deliveryStatus()).isEqualTo("ABSENT");
        String[] expected={"DRIVER_ACCEPTED","ALREADY_PICKED_UP","ALREADY_DELIVERED","CANCELLATION_PENDING","ALREADY_CANCELLED","PURCHASE_COMPLETED","PAYMENT_PENDING","ORDER_EXPIRED","STATE_NOT_SYNCHRONIZED"};
        for(int i=0;i<expected.length;i++) assertThat(orders.getContext(920001L,930004L+i).reasonCode()).isEqualTo(expected[i]);
        LocalDate yesterday=LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        var date=orders.findMyOrders(920001L,OrderSupportReader.Search.browse(null, "평가 날짜소파", yesterday, yesterday, null, 5));
        assertThat(date.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(930042L,930041L);
        assertThat(date.hasUndatedOrders()).isTrue();
        var first=orders.findMyOrders(920001L,OrderSupportReader.Search.browse(null, "평가 페이지의자", null, null, null, 5));
        assertThat(first.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(930026L,930025L,930024L,930023L,930022L);
        var second=orders.findMyOrders(920001L,OrderSupportReader.Search.browse(null, "평가 페이지의자", null, null, first.nextCursor(), 5));
        assertThat(second.orders()).extracting(OrderSupportReader.Candidate::orderId).containsExactly(930021L,930020L);
        assertThat(orders.findMyOrders(920001L,OrderSupportReader.Search.browse(939001L, null, null, null, null, 5)).orders()).isEmpty();
        assertThat(orders.getContext(920001L,930013L).eligibility()).isEqualTo(OrderSupportReader.Eligibility.ELIGIBLE);
        jdbc.update("UPDATE delivery SET status='ACCEPTED',accepted_at=UTC_TIMESTAMP(6) WHERE order_id=930013");
        assertThat(orders.getContext(920001L,930013L).reasonCode()).isEqualTo("DRIVER_ACCEPTED");
        assertThat(orders.getContext(920001L,930014L).reasonCode()).isEqualTo("ALREADY_PICKED_UP");
    }
}
