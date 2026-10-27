package setty.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class FlywayStartupTest extends MySqlIntegrationTestSupport {

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void applicationStartsWithFlywayAndHibernateValidationOnly() {
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(flyway.getConfiguration().isBaselineOnMigrate()).isFalse();
        assertThat(flyway.getConfiguration().getBaselineVersion().getVersion()).isEqualTo("0");
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("12");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE type='SQL' AND success=1", Integer.class))
                .isEqualTo(12);
    }
}
