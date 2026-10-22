package setty.platform.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import setty.address.AddressService;
import setty.platform.member.service.AddressBackfillJob;
import setty.platform.member.service.AddressBackfillService;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class AddressBackfillJobTest extends MySqlIntegrationTestSupport {

    private static final String CITY_HALL = "1114010300100310000019224";
    private static final String CITY_HALL_DONG = "1114055000";
    private static final String MATCHED_ADDRESS = "서울 중구 세종대로 110";

    @Autowired
    private AddressBackfillService backfillService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @MockitoSpyBean
    private AddressService addressService;

    private AddressBackfillJob job;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO address_reference (mgmt_no, road_code, road_name, underground, building_main, building_sub,
                    sigungu, legal_dong, jibun_mountain, jibun_main, jibun_sub, zip_code, dong_code)
                VALUES (?, '111402005001', '세종대로', 0, 110, 0, '중구', '태평로1가', 0, 31, 0, '04524', ?)
                """, CITY_HALL, CITY_HALL_DONG);
        job = new AddressBackfillJob(backfillService, 2, Duration.ZERO);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM members");
        jdbcTemplate.update("DELETE FROM address_reference");
    }

    @Test
    void 표준_주소가_빈_회원을_묶음으로_모두_채운다() {
        insertMember(1, MATCHED_ADDRESS, null);
        insertMember(2, "가상 주소", null);
        insertMember(3, MATCHED_ADDRESS, "1111053000");
        insertMember(4, MATCHED_ADDRESS, null);
        insertMember(5, "가상 주소", null);

        assertThat(job.run()).isEqualTo(5);

        assertThat(statuses()).containsExactly(
                Map.entry(1L, "MATCHED"), Map.entry(2L, "UNMATCHED"), Map.entry(3L, "MATCHED"),
                Map.entry(4L, "MATCHED"), Map.entry(5L, "UNMATCHED"));
        assertThat(dongCode(1)).isEqualTo(CITY_HALL_DONG);
        assertThat(dongCode(3)).isEqualTo("1111053000");
        assertThat(dongCode(2)).isNull();
    }

    @Test
    void 중간에_멈췄다가_다시_돌리면_남은_회원만_이어서_채운다() {
        for (long id = 1; id <= 5; id++) {
            insertMember(id, MATCHED_ADDRESS, null);
        }
        backfillService.backfillAfter(0, 2);

        assertThat(job.run()).isEqualTo(3);

        assertThat(statuses().values()).containsOnly("MATCHED");
    }

    @Test
    void 두_번_돌려도_결과가_같다() {
        insertMember(1, MATCHED_ADDRESS, null);
        insertMember(2, "가상 주소", null);
        job.run();
        final List<Map<String, Object>> first = members();

        assertThat(job.run()).isZero();

        assertThat(members()).isEqualTo(first);
    }

    @Test
    void 이미_표준_주소가_있는_회원은_건드리지_않는다() {
        insertMember(1, MATCHED_ADDRESS, null);
        jdbcTemplate.update("UPDATE members SET address_status = 'AMBIGUOUS' WHERE id = 1");

        assertThat(job.run()).isZero();

        assertThat(statuses()).containsExactly(Map.entry(1L, "AMBIGUOUS"));
    }

    @Test
    void 다른_인스턴스가_잠근_회원은_건너뛴다() throws Exception {
        insertMember(1, MATCHED_ADDRESS, null);
        insertMember(2, MATCHED_ADDRESS, null);
        insertMember(3, MATCHED_ADDRESS, null);

        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            other.createStatement().executeQuery("SELECT id FROM members WHERE id = 2 FOR UPDATE");

            assertThat(job.run()).isEqualTo(2);

            other.rollback();
        }
        final Map<Long, String> statuses = statuses();
        assertThat(statuses.get(1L)).isEqualTo("MATCHED");
        assertThat(statuses.get(2L)).isNull();
        assertThat(statuses.get(3L)).isEqualTo("MATCHED");
    }

    @Test
    void 묶음을_처리하는_동안에도_해석된_회원_수정과_가입은_기다리지_않는다() throws Exception {
        final CountDownLatch resolving = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            resolving.countDown();
            release.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(addressService).resolve("멈추는 주소");
        insertMember(1, MATCHED_ADDRESS, null);
        jdbcTemplate.update("UPDATE members SET address_status = 'MATCHED' WHERE id = 1");
        insertMember(2, "멈추는 주소", null);

        final CompletableFuture<List<Long>> batch = CompletableFuture.supplyAsync(() -> backfillService.backfillAfter(0, 2));
        try (Connection other = dataSource.getConnection(); Statement statement = other.createStatement()) {
            assertThat(resolving.await(10, TimeUnit.SECONDS)).isTrue();
            statement.execute("SET SESSION innodb_lock_wait_timeout = 1");

            assertThat(statement.executeUpdate("UPDATE members SET token = 'login-token' WHERE id = 1")).isOne();
            assertThat(statement.executeUpdate("""
                    INSERT INTO members (id, login_id, password, role, phone_number, address)
                    VALUES (100, 'member100', 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소')
                    """)).isOne();
        } finally {
            release.countDown();
        }

        assertThat(batch.get(10, TimeUnit.SECONDS)).containsExactly(2L);
    }

    @Test
    void 묶음이_실패하면_한_건씩_다시_시도해_나머지는_채운다() {
        doThrow(new IllegalStateException("해석 실패")).when(addressService).resolve("실패하는 주소");
        insertMember(1, MATCHED_ADDRESS, null);
        insertMember(2, "실패하는 주소", null);
        insertMember(3, MATCHED_ADDRESS, null);
        insertMember(4, MATCHED_ADDRESS, null);

        assertThat(job.run()).isEqualTo(4);

        final Map<Long, String> statuses = statuses();
        assertThat(statuses.get(1L)).isEqualTo("MATCHED");
        assertThat(statuses.get(2L)).isNull();
        assertThat(statuses.get(3L)).isEqualTo("MATCHED");
        assertThat(statuses.get(4L)).isEqualTo("MATCHED");
    }

    private void insertMember(final long id, final String address, final String dongCode) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, dong_code)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', ?, ?)
                """, id, "member" + id, address, dongCode);
    }

    private Map<Long, String> statuses() {
        final Map<Long, String> statuses = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT id, address_status FROM members ORDER BY id",
                rs -> {
                    statuses.put(rs.getLong("id"), rs.getString("address_status"));
                });
        return statuses;
    }

    private String dongCode(final long id) {
        return jdbcTemplate.queryForObject("SELECT dong_code FROM members WHERE id = ?", String.class, id);
    }

    private List<Map<String, Object>> members() {
        return jdbcTemplate.queryForList("SELECT * FROM members ORDER BY id");
    }
}
