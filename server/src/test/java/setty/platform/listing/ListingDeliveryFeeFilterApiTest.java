package setty.platform.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import setty.deliveryfee.DeliveryFeeService;
import setty.deliveryfee.Dong;
import setty.deliveryfee.VolumeTier;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ListingDeliveryFeeFilterApiTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 101L;
    private static final long BUYER_ID = 102L;
    private static final long NO_DONG_BUYER_ID = 103L;
    private static final String BUYER_DONG = "1111053000";
    private static final List<String> SELLER_DONGS = List.of("1111053000", "1111054000", "1156054000", "1171068000", "1132052100");
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00.123456Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeliveryFeeService deliveryFeeService;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    private final List<Listing> listings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(SELLER_ID, null);
        insertMember(BUYER_ID, BUYER_DONG);
        insertMember(NO_DONG_BUYER_ID, null);
        when(listingImageStorage.publicUrl(anyString()))
                .thenAnswer(invocation -> "https://images.example.test/" + invocation.getArgument(0));

        long id = 1;
        for (String dongCode : SELLER_DONGS) {
            for (VolumeTier volumeTier : VolumeTier.values()) {
                insertListing(new Listing(id, BASE.plusSeconds(id), dongCode, volumeTier));
                id++;
            }
        }
        insertListing(new Listing(id, BASE.plusSeconds(id), null, VolumeTier.SMALL));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
        listings.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {10_000, 20_000, 25_000, 40_000})
    void 예산_안에_드는_매물만_최신순으로_준다(int maxDeliveryFee) throws Exception {
        JsonNode page = read(filtered(maxDeliveryFee).param("size", "100"));

        assertThat(ids(page)).containsExactlyElementsOf(expectedIds(maxDeliveryFee));
        assertThat(ids(page)).isNotEmpty();
    }

    @Test
    void 커서로_이어_받아도_예산_안의_매물을_빠짐없이_받는다() throws Exception {
        List<Long> received = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request = filtered(25_000).param("size", "2");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = read(request);
            received.addAll(ids(page));
            cursor = page.has("nextCursor") ? page.get("nextCursor").asString() : null;
        } while (cursor != null);

        assertThat(received).containsExactlyElementsOf(expectedIds(25_000));
    }

    @Test
    void 기본_배송비보다_작은_예산이면_빈_목록이다() throws Exception {
        assertThat(ids(read(filtered(9_999)))).isEmpty();
    }

    @Test
    void 로그인하지_않으면_배송비_필터를_쓸_수_없다() throws Exception {
        mockMvc.perform(get("/api/listings").param("maxDeliveryFee", "20000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 행정동을_설정하지_않은_회원은_배송비_필터를_쓸_수_없다() throws Exception {
        mockMvc.perform(get("/api/listings")
                        .param("maxDeliveryFee", "20000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + NO_DONG_BUYER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_DONG_REQUIRED"));
    }

    @Test
    void 필터_없이_부르면_로그인_없이_전체를_준다() throws Exception {
        assertThat(ids(read(get("/api/listings")))).hasSize(listings.size());
    }

    private List<Long> expectedIds(int maxDeliveryFee) {
        Dong buyer = deliveryFeeService.findDong(BUYER_DONG).orElseThrow();
        return listings.stream()
                .filter(listing -> listing.dongCode() != null)
                .filter(listing -> deliveryFeeService.fee(
                        deliveryFeeService.findDong(listing.dongCode()).orElseThrow().index(),
                        buyer.index(),
                        listing.volumeTier()) <= maxDeliveryFee)
                .sorted(Comparator.comparing(Listing::createdAt).reversed())
                .map(Listing::id)
                .toList();
    }

    private MockHttpServletRequestBuilder filtered(int maxDeliveryFee) {
        return get("/api/listings")
                .param("maxDeliveryFee", String.valueOf(maxDeliveryFee))
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID);
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        byte[] body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        return objectMapper.readTree(body);
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private void insertMember(long id, String dongCode) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, dong_code, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?, ?)
                """, id, "member" + id, dongCode, "token-" + id);
    }

    // 부피 구간은 생성 컬럼이라 높이로 맞춘다(가로·세로 100cm: 30cm = SMALL, 50cm = MEDIUM, 150cm = LARGE)
    private void insertListing(Listing listing) {
        int height = switch (listing.volumeTier()) {
            case SMALL -> 30;
            case MEDIUM -> 50;
            case LARGE -> 150;
        };
        Timestamp at = Timestamp.from(listing.createdAt());
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category, condition_grade,
                                      width_cm, depth_cm, height_cm, sale_status, has_purchase_request,
                                      created_at, updated_at, dong_code)
                VALUES (?, ?, ?, '테스트용 가상 매물입니다', 120000, 10000, 'CHAIR', 'A', 100, 100, ?, 'AVAILABLE', false, ?, ?, ?)
                """, listing.id(), SELLER_ID, "가상 매물 " + listing.id(), height, at, at, listing.dongCode());
        listings.add(listing);
    }

    private record Listing(long id, Instant createdAt, String dongCode, VolumeTier volumeTier) {
    }
}
