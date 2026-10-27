package setty.platform.listing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import setty.platform.listing.domain.DeliveryFeePolicy;
import setty.platform.listing.domain.Dimensions;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
class ListingVolumeTierColumnTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 101L;
    private static final Map<Integer, String> TIER_BY_FEE = Map.of(10_000, "SMALL", 20_000, "MEDIUM", 30_000, "LARGE");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, 'seller', 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', NULL)
                """, SELLER_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @ParameterizedTest
    @CsvSource({
            "100, 100, 30",
            "101, 100, 30",
            "100, 100, 100",
            "101, 100, 100"
    })
    void 부피_구간_컬럼은_매물_배송비_정책과_같은_경계로_나뉜다(final int width, final int depth, final int height) {
        jdbcTemplate.update("""
                INSERT INTO listings (seller_id, title, description, price, delivery_fee, category, condition_grade,
                                      width_cm, depth_cm, height_cm, sale_status, has_purchase_request,
                                      created_at, updated_at)
                VALUES (?, '가상 매물', '테스트용 가상 매물입니다', 120000, 10000, 'CHAIR', 'A', ?, ?, ?, 'AVAILABLE', false,
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """, SELLER_ID, width, depth, height);

        final String volumeTier = jdbcTemplate.queryForObject("SELECT volume_tier FROM listings", String.class);

        assertThat(volumeTier).isEqualTo(TIER_BY_FEE.get(DeliveryFeePolicy.calculate(Dimensions.of(width, depth, height))));
    }
}
