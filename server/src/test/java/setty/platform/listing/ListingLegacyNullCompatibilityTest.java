package setty.platform.listing;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.Dimensions;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.ListingCategory;

@Testcontainers
class ListingLegacyNullCompatibilityTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11");

    @Test
    void currentMappingReadsAndUpdatesARowWhoseLegacyTitleIsNull() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(source);
        // 기존 컬럼이 NULL인 호환 fixture를 별도 DB에 준비한다.
        Flyway.configure().dataSource(source).target("11").load().migrate();
        jdbc.execute("ALTER TABLE listings MODIFY COLUMN title VARCHAR(100) NULL");
        jdbc.update("""
                INSERT INTO members(id,login_id,password,role,phone_number,address)
                VALUES (1,'nullable-title','encoded','MEMBER','010-0000-0000','synthetic')
                """);
        var configuration = new Configuration().addAnnotatedClass(Listing.class)
                .setProperty("hibernate.connection.url", MYSQL.getJdbcUrl())
                .setProperty("hibernate.connection.username", MYSQL.getUsername())
                .setProperty("hibernate.connection.password", MYSQL.getPassword())
                .setProperty("hibernate.hbm2ddl.auto", "validate");
        try (var factory = configuration.buildSessionFactory()) {
            Long id;
            try (var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                Listing listing = Listing.create(1L, "1111053000", null, "new-only-row", "synthetic",
                        50000, ListingCategory.CHAIR, ConditionGrade.A, Dimensions.of(40, 40, 80));
                session.persist(listing);
                transaction.commit();
                id = listing.getId();
            }
            jdbc.update("UPDATE listings SET title=NULL WHERE id=?", id);
            try (var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                Listing listing = session.find(Listing.class, id);
                assertThat(listing.getTitle()).isEqualTo("new-only-row");
                listing.update("changed", "synthetic", 50000, ListingCategory.CHAIR,
                        ConditionGrade.A, Dimensions.of(40, 40, 80));
                transaction.commit();
            }
            assertThat(jdbc.queryForMap("SELECT title,display_title FROM listings WHERE id=?", id))
                    .containsEntry("title", null).containsEntry("display_title", "changed");
        }
    }
}
