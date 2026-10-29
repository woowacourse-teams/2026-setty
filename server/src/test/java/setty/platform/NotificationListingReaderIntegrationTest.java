package setty.platform;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import setty.platform.listing.domain.DeliveryFeePolicy;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.SaleStatus;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;

@SpringBootTest
@Transactional
class NotificationListingReaderIntegrationTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 817_001L;
    private static final long LISTING_ID = 817_100L;

    @Autowired
    private NotificationListingReader reader;
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address)
                VALUES (?, 'projection-seller', 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소')
                """, SELLER_ID);
    }

    @Test
    void 모든_판매상태와_삭제_선점_조합에서_기존_엔티티_조회와_결과가_같다() {
        long id = LISTING_ID;
        for (SaleStatus status : SaleStatus.values()) {
            for (boolean deleted : List.of(false, true)) {
                for (boolean reserved : List.of(false, true)) {
                    insertListing(id, 100, 100, 30, status, reserved, deleted);
                    id++;
                }
            }
        }
        List<Long> ids = java.util.stream.LongStream.range(LISTING_ID, id).boxed().toList();
        assertThat(reader.findAll(ids)).isEqualTo(previousResult(ids));
        assertThat(reader.findAll(ids)).hasSize(12);
    }

    @ParameterizedTest
    @CsvSource({"1,1,1", "100,100,30", "100,100,31", "100,100,100", "100,100,101", "1000,1000,1000"})
    void 기존_배송비_부피_계산과_생성_컬럼은_경계에서도_같다(int width, int depth, int height) {
        insertListing(LISTING_ID, width, depth, height, SaleStatus.AVAILABLE, false, false);
        assertThat(reader.findAll(List.of(LISTING_ID))).isEqualTo(previousResult(List.of(LISTING_ID)));
    }

    @Test
    void 지역이_없거나_중복된_ID와_없는_ID도_기존_결과를_유지한다() {
        insertListing(LISTING_ID, 100, 100, 30, SaleStatus.AVAILABLE, false, true);
        jdbcTemplate.update("UPDATE listings SET dong_code = NULL, zip_zone = NULL WHERE id = ?", LISTING_ID);
        List<Long> ids = List.of(LISTING_ID, LISTING_ID, -1L);
        assertThat(reader.findAll(ids)).isEqualTo(previousResult(ids)).hasSize(1);
        assertThat(reader.findAll(ids).get(LISTING_ID).dongCode()).isNull();
        assertThat(reader.findAll(ids).get(LISTING_ID).zipZone()).isNull();
        assertThat(reader.findAll(List.of(-1L))).isEmpty();
        assertThat(reader.findAll(List.of())).isEmpty();
    }

    @Test
    void 매물_엔티티를_생성하지_않고_같은_스냅샷을_반환한다() {
        insertListing(LISTING_ID, 100, 100, 101, SaleStatus.RESERVED, true, false);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            entityManager.clear();
            statistics.clear();
            Map<Long, NotifiedListing> previous = previousResult(List.of(LISTING_ID));
            assertThat(statistics.getEntityStatistics(Listing.class.getName()).getLoadCount()).isEqualTo(1);
            entityManager.clear();
            statistics.clear();
            assertThat(reader.findAll(List.of(LISTING_ID))).isEqualTo(previous);
            assertThat(statistics.getEntityStatistics(Listing.class.getName()).getLoadCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    // PR #555의 기존 엔티티 조회와 도메인 계산을 비교 기준으로 보존한다.
    private Map<Long, NotifiedListing> previousResult(List<Long> ids) {
        return listingRepository.findAllById(ids).stream().map(listing -> new NotifiedListing(
                listing.getId(), listing.getTitle(), listing.getPrice(), listing.getDongCode(), listing.getZipZone(),
                DeliveryFeePolicy.volumeTier(listing.getDimensions()), listing.getDeliveryFee(),
                listing.isDeleted() ? "DELETED"
                        : listing.getSaleStatus() == SaleStatus.SOLD ? "SOLD"
                        : listing.getSaleStatus() == SaleStatus.RESERVED || listing.hasPurchaseRequest() ? "TRADING"
                        : "AVAILABLE"
        )).collect(Collectors.toMap(NotifiedListing::id, listing -> listing));
    }

    private void insertListing(long id, int width, int depth, int height, SaleStatus status, boolean reserved, boolean deleted) {
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category, condition_grade,
                        width_cm, depth_cm, height_cm, sale_status, has_purchase_request,
                        created_at, updated_at, deleted_at, dong_code, zip_zone)
                VALUES (?, ?, '원목 책상', ?, 150000, 20000, 'DESK', 'A', ?, ?, ?, ?, ?,
                        NOW(6), NOW(6), IF(?, NOW(6), NULL), '1111053000', 123)
                """, id, SELLER_ID, "조회에서 제외할 설명".repeat(50), width, depth, height, status.name(), reserved, deleted);
    }
}
