package setty.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class FlywayMigrationTest {

    private static final List<String> STANDARD_ADDRESS_COLUMNS = List.of("address_mgmt_no", "road_address",
            "address_detail", "zip_code", "latitude", "longitude", "address_status");
    private static final List<String> ORDER_PICKUP_SNAPSHOT_COLUMNS = List.of("pickup_address", "pickup_phone_number",
            "pickup_dong_code", "pickup_zip_code", "pickup_address_mgmt_no", "delivery_zip_code",
            "delivery_address_mgmt_no");
    private static final List<String> DELIVERY_ZONE_COLUMNS = List.of("pickup_dong_code", "pickup_zip_code",
            "delivery_dong_code", "delivery_zip_code");
    private static final List<String> DELIVERY_CELL_COLUMNS = List.of("pickup_cell", "delivery_cell");
    private static final List<String> KEYWORD_CONDITION_COLUMNS = List.of("max_delivery_fee", "max_total_price");
    private static final int MIGRATION_COUNT = 20;

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.11");

    private String database;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createIsolatedDatabase() throws Exception {
        database = "flyway_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        dataSource = new DriverManagerDataSource(
                "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database,
                "root", MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void dropOnlyThisTestsDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE " + database);
        }
    }

    @Test
    void emptyDatabaseMigratesAndRestartDoesNotRerunScripts() {
        var flyway = flyway(false);

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(MIGRATION_COUNT);
        assertCurrentSchema();
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE type='SQL' AND success=1"))
                .isEqualTo(MIGRATION_COUNT);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void existingDatabaseRequiresExplicitAdoption() {
        legacy("pre-dong-main.sql");
        seedExistingRows();

        assertThatThrownBy(() -> flyway(false).migrate())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema");
        assertThat(count("SELECT COUNT(*) FROM members WHERE id=1")).isOne();
        assertThat(columnCount("members", "dong_code")).isZero();
    }

    @Test
    void preDongSchemaUpgradesWithoutLosingRowsOrInventingDongValues() {
        legacy("pre-dong-main.sql");
        seedExistingRows();

        assertThat(flyway(true).migrate().migrationsExecuted).isEqualTo(MIGRATION_COUNT);

        assertCurrentSchema();
        assertThat(jdbc.queryForObject("SELECT title FROM listings WHERE id=1", String.class))
                .isEqualTo("migration fixture");
        assertThat(jdbc.queryForObject("SELECT dong_code FROM listings WHERE id=1", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT volume_tier FROM listings WHERE id=1", String.class))
                .isEqualTo("SMALL");
        assertThat(jdbc.queryForObject("SELECT delivery_fee FROM orders WHERE id=1", Integer.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE id=1", Integer.class))
                .isEqualTo(130000);
        assertThat(jdbc.queryForMap("SELECT pickup_address, pickup_dong_code, pickup_zip_code, delivery_dong_code, "
                + "delivery_zip_code FROM delivery WHERE id=1"))
                .containsEntry("pickup_address", "synthetic pickup")
                .containsEntry("pickup_dong_code", null).containsEntry("pickup_zip_code", null)
                .containsEntry("delivery_dong_code", null).containsEntry("delivery_zip_code", null);
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE type='BASELINE' AND version='0'"))
                .isOne();
        assertThat(flyway(false).migrate().migrationsExecuted).isZero();
    }

    @Test
    void alreadyUpdatedSchemaKeepsSchemaAndDeliverySnapshot() {
        legacy("pre-flyway-develop.sql");
        seedExistingRows();
        jdbc.update("UPDATE members SET dong_code='1111053000' WHERE id=1");
        jdbc.update("UPDATE listings SET dong_code='1111053000' WHERE id=1");
        jdbc.update("""
                UPDATE orders SET delivery_fee=15000, delivery_dong_code='1111054000',
                delivery_address='synthetic delivery address', delivery_phone_number='010-0000-0000'
                WHERE id=1
                """);
        var schemaBefore = schemaSnapshot();
        var orderBefore = jdbc.queryForMap("SELECT * FROM orders WHERE id=1");

        assertThat(flyway(true).migrate().migrationsExecuted).isEqualTo(MIGRATION_COUNT);

        assertThat(schemaSnapshot().stream().filter(column -> !addedAfterAdoption(column)).toList())
                .isEqualTo(schemaBefore);
        assertCurrentSchema();
        var orderAfter = jdbc.queryForMap("SELECT * FROM orders WHERE id=1");
        assertThat(orderAfter).containsAllEntriesOf(orderBefore);
        assertThat(ORDER_PICKUP_SNAPSHOT_COLUMNS).allSatisfy(column -> assertThat(orderAfter.get(column)).isNull());
        assertThat(jdbc.queryForObject("SELECT dong_code FROM listings WHERE id=1", String.class))
                .isEqualTo("1111053000");
        assertThat(flyway(false).migrate().migrationsExecuted).isZero();
    }

    @Test
    void partiallyAddedOrderSnapshotGetsRemainingColumns() {
        legacy("pre-dong-main.sql");
        seedExistingRows();
        jdbc.execute("ALTER TABLE orders ADD COLUMN delivery_fee INT NULL");
        jdbc.update("UPDATE orders SET delivery_fee=15000 WHERE id=1");

        flyway(true).migrate();

        assertCurrentSchema();
        assertThat(jdbc.queryForObject("SELECT delivery_fee FROM orders WHERE id=1", Integer.class))
                .isEqualTo(15000);
    }

    @Test
    void laterMigrationsUpgradeVersionNineWithoutChangingExistingDelivery() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("9").cleanDisabled(true).load().migrate();
        seedExistingRows();
        var deliveryBefore = jdbc.queryForMap("SELECT * FROM delivery WHERE id=1");

        assertThat(flyway(false).migrate().migrationsExecuted).isEqualTo(MIGRATION_COUNT - 9);

        assertCurrentSchema();
        // 기존 배송 행은 그대로이고, 나중에 더한 칸 번호만 NULL로 생긴다.
        var deliveryAfter = new HashMap<>(jdbc.queryForMap("SELECT * FROM delivery WHERE id=1"));
        assertThat(deliveryAfter.remove("pickup_cell")).isNull();
        assertThat(deliveryAfter.remove("delivery_cell")).isNull();
        assertThat(deliveryAfter).isEqualTo(new HashMap<>(deliveryBefore));
        assertThat(flyway(false).migrate().migrationsExecuted).isZero();
    }

    @Test
    void loginTokenExpiryGivesExistingTokensThirtyDaysAndLeavesTokenlessRowsEmpty() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("18").cleanDisabled(true).load().migrate();
        jdbc.update("""
                INSERT INTO members(id,login_id,password,role,phone_number,address,token)
                VALUES (1,'logged-in','encoded-password','MEMBER','010-0000-0000','synthetic address','member-token'),
                       (2,'logged-out','encoded-password','MEMBER','010-0000-0000','synthetic address',NULL)
                """);
        jdbc.update("""
                INSERT INTO delivery_member(id,login_id,password,phone_number,license_plate_number,car_type,
                    business_registration_number,token)
                VALUES (1,'driver-in','encoded-password','010-0000-0000','12가3456','다마스','000-00-00000','driver-token'),
                       (2,'driver-out','encoded-password','010-0000-0000','12가3457','다마스','000-00-00001',NULL)
                """);

        flyway(false).migrate();

        final String remainingDays = "SELECT TIMESTAMPDIFF(HOUR, UTC_TIMESTAMP(6), token_expires_at) FROM %s WHERE id=1";
        assertThat(jdbc.queryForObject(remainingDays.formatted("members"), Integer.class)).isBetween(719, 720);
        assertThat(jdbc.queryForObject(remainingDays.formatted("delivery_member"), Integer.class)).isBetween(719, 720);
        assertThat(count("SELECT COUNT(*) FROM members WHERE id=2 AND token_expires_at IS NULL")).isOne();
        assertThat(count("SELECT COUNT(*) FROM delivery_member WHERE id=2 AND token_expires_at IS NULL")).isOne();
    }

    @Test
    void changingAnAppliedMigrationIsRejected(@TempDir final Path directory) throws Exception {
        flyway(false).migrate();
        for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql")) {
            try (var input = resource.getInputStream()) {
                Files.copy(input, directory.resolve(resource.getFilename()));
            }
        }
        var changed = directory.resolve("V1__adopt_platform_schema.sql");
        Files.writeString(changed, Files.readString(changed) + "\nSELECT 123456;\n");
        var changedFlyway = Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + directory).load();

        assertThatThrownBy(changedFlyway::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("checksum mismatch");
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE type='SQL' AND success=1"))
                .isEqualTo(MIGRATION_COUNT);
    }

    private Flyway flyway(final boolean adoptExisting) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .baselineVersion("0").baselineOnMigrate(adoptExisting)
                .cleanDisabled(true).validateMigrationNaming(true).load();
    }

    private void legacy(final String filename) {
        new ResourceDatabasePopulator(new ClassPathResource("db/legacy/" + filename)).execute(dataSource);
    }

    private void seedExistingRows() {
        jdbc.update("""
                INSERT INTO members(id,login_id,password,role,phone_number,address)
                VALUES (1,'migration-user','encoded-password','MEMBER','010-0000-0000','synthetic address')
                """);
        jdbc.update("""
                INSERT INTO listings(id,seller_id,title,description,price,delivery_fee,category,condition_grade,
                width_cm,depth_cm,height_cm,sale_status,has_purchase_request,created_at,updated_at)
                VALUES (1,1,'migration fixture','synthetic',120000,10000,'CHAIR','A',10,10,10,
                'RESERVED',true,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO orders(id,listing_id,buyer_id,delivery_status,order_status)
                VALUES (1,1,1,'REQUESTED','CONFIRMED')
                """);
        jdbc.update("""
                INSERT INTO delivery(id,order_id,item_name,category,pickup_address,delivery_address,
                pickup_phone_number,delivery_phone_number,estimated_fee,status,requested_at)
                VALUES (1,1,'migration fixture','CHAIR','synthetic pickup','synthetic destination',
                '010-0000-0001','010-0000-0002',10000,'REQUESTED',CURRENT_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO payments(id,order_id,payment_key,toss_order_id,amount,status,approved_at)
                VALUES (1,1,'synthetic-key','synthetic-order',130000,'DONE',CURRENT_TIMESTAMP)
                """);
    }

    private void assertCurrentSchema() {
        assertThat(columnCount("members", "dong_code")).isOne();
        for (var column : STANDARD_ADDRESS_COLUMNS) {
            assertThat(columnCount("members", column)).as("members.%s", column).isOne();
        }
        assertThat(indexColumns("address_reference", "idx_address_reference_road"))
                .isEqualTo("road_name,building_main,building_sub");
        assertThat(indexColumns("address_reference", "idx_address_reference_jibun"))
                .isEqualTo("legal_dong,jibun_main,jibun_sub");
        assertThat(columnCount("listings", "dong_code")).isOne();
        assertThat(columnCount("listings", "volume_tier")).isOne();
        for (var column : List.of("delivery_fee", "delivery_dong_code", "delivery_address", "delivery_phone_number")) {
            assertThat(columnCount("orders", column)).as("orders.%s", column).isOne();
        }
        for (var column : ORDER_PICKUP_SNAPSHOT_COLUMNS) {
            assertThat(columnCount("orders", column)).as("orders.%s", column).isOne();
        }
        for (var column : DELIVERY_ZONE_COLUMNS) {
            assertThat(columnCount("delivery", column)).as("delivery.%s", column).isOne();
        }
        for (var column : KEYWORD_CONDITION_COLUMNS) {
            assertThat(columnCount("keyword_subscriptions", column)).as("keyword_subscriptions.%s", column).isOne();
        }
        assertThat(columnCount("listing_notifications", "read_at")).isOne();
        assertThat(columnCount("members", "withdrawn_at")).isOne();
        assertThat(columnCount("payments", "attempted_at")).isOne();
        assertThat(indexColumns("payments", "idx_payments_unknown")).isEqualTo("status,attempted_at");
        assertThat(indexColumns("payment_refunds", "uk_payment_refunds_payment")).isEqualTo("payment_id");
        assertThat(indexColumns("order_notifications", "uk_order_notifications_event"))
                .isEqualTo("order_id,member_id,kind,occurred_at");
        assertThat(indexColumns("delivery", "idx_delivery_requests"))
                .isEqualTo("status,driver_id,requested_at,id");
        assertThat(indexColumns("delivery", "idx_delivery_shipments"))
                .isEqualTo("driver_id,accepted_at,id");
        for (var column : DELIVERY_CELL_COLUMNS) {
            assertThat(columnCount("delivery", column)).as("delivery.%s", column).isOne();
        }
        assertThat(indexColumns("delivery", "idx_delivery_cells"))
                .isEqualTo("pickup_cell,delivery_cell,status");
        assertThat(indexColumns("listings", "idx_listings_delivery_filter"))
                .isEqualTo("deleted_at,sale_status,created_at,id,dong_code,volume_tier");
        assertThat(columnCount("listings", "zip_zone")).isOne();
        assertThat(indexColumns("listings", "idx_listings_zip_zone_filter"))
                .isEqualTo("deleted_at,sale_status,created_at,id,zip_zone,volume_tier");
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
                AND table_name='listings' AND index_name='idx_listings_public'
                """)).isZero();
        for (var table : List.of("keyword_subscriptions", "keyword_exclusions", "listing_notifications", "device_tokens",
                "delivery_order_decision", "settlements", "settlement_order", "EVENT_PUBLICATION")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name=?", Integer.class, table)).as(table).isOne();
        }
    }

    private int columnCount(final String table, final String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", Integer.class, table, column);
    }

    private String indexColumns(final String table, final String index) {
        return jdbc.queryForObject("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) "
                + "FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=?",
                String.class, table, index);
    }

    private boolean addedAfterAdoption(final Map<String, Object> column) {
        var table = (String) column.get("table_name");
        var name = (String) column.get("column_name");
        return table.equals("address_reference")
                || table.equals("members") && STANDARD_ADDRESS_COLUMNS.contains(name)
                || table.equals("listings") && name.equals("zip_zone")
                || table.equals("orders") && ORDER_PICKUP_SNAPSHOT_COLUMNS.contains(name)
                || table.equals("delivery") && DELIVERY_ZONE_COLUMNS.contains(name)
                || table.equals("delivery") && DELIVERY_CELL_COLUMNS.contains(name)
                || table.equals("keyword_subscriptions") && KEYWORD_CONDITION_COLUMNS.contains(name)
                || table.equals("keyword_exclusions")
                || table.equals("order_notifications")
                || table.equals("payment_refunds")
                || table.equals("payments") && name.equals("attempted_at")
                || table.equals("listing_notifications") && name.equals("read_at")
                || table.equals("members") && name.equals("withdrawn_at")
                || List.of("members", "delivery_member").contains(table) && name.equals("token_expires_at");
    }

    private int count(final String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private List<Map<String, Object>> schemaSnapshot() {
        return jdbc.queryForList("""
                SELECT table_name,column_name,column_type,is_nullable,column_default,extra,generation_expression
                FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history'
                ORDER BY table_name,ordinal_position
                """);
    }
}
