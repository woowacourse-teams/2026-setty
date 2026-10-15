package setty.platform.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import setty.deliveryfee.DeliveryFeeService;
import setty.deliveryfee.Dong;
import setty.deliveryfee.VolumeTier;
import setty.platform.PaymentOrderReader;
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
    private static final int PRICE = 120_000;
    private static final int FIXED_FEE = 10_000;
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00.123456Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeliveryFeeService deliveryFeeService;

    @Autowired
    private PaymentOrderReader paymentOrderReader;

    @Autowired
    private MeterRegistry meterRegistry;

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
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM favorites");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
        jdbcTemplate.update("DELETE FROM delivery_member");
        listings.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {10_000, 20_000, 25_000, 40_000})
    void 예산_안에_드는_매물만_최신순으로_준다(int maxDeliveryFee) throws Exception {
        JsonNode page = read(filtered(maxDeliveryFee).param("size", "100"));

        assertThat(ids(page)).containsExactlyElementsOf(expectedIds(maxDeliveryFee));
        assertThat(ids(page)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {10_000, 20_000, 25_000, 40_000})
    void 필터로_걸린_매물의_표시_배송비는_예산_이하인_거리_기준_금액이다(int maxDeliveryFee) throws Exception {
        JsonNode page = read(filtered(maxDeliveryFee).param("size", "100"));

        assertThat(page.get("items")).isNotEmpty().allSatisfy(item -> {
            int deliveryFee = item.get("deliveryFee").asInt();
            assertThat(deliveryFee).isLessThanOrEqualTo(maxDeliveryFee)
                    .isEqualTo(expectedFee(listingOf(item.get("id").asLong())));
            assertThat(item.get("totalPrice").asInt()).isEqualTo(PRICE + deliveryFee);
        });
    }

    @Test
    void 로그인한_구매자에게는_필터_없이도_거리_기준_배송비를_보여_준다() throws Exception {
        JsonNode page = read(get("/api/listings").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID));

        assertThat(page.get("items")).hasSize(listings.size()).allSatisfy(item -> assertThat(item.get("deliveryFee").asInt())
                .isEqualTo(expectedFee(listingOf(item.get("id").asLong()))));
        assertThat(page.get("items")).anySatisfy(item -> assertThat(item.get("deliveryFee").asInt()).isNotEqualTo(FIXED_FEE));
    }

    @Test
    void 비로그인이거나_구매자_행정동이_없으면_등록_때_배송비를_보여_준다() throws Exception {
        JsonNode anonymous = read(get("/api/listings").param("size", "100"));
        JsonNode noDongBuyer = read(get("/api/listings").param("size", "100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + NO_DONG_BUYER_ID));

        assertThat(anonymous.get("items")).allSatisfy(item -> assertThat(item.get("deliveryFee").asInt()).isEqualTo(FIXED_FEE));
        assertThat(noDongBuyer.get("items")).allSatisfy(item -> assertThat(item.get("deliveryFee").asInt()).isEqualTo(FIXED_FEE));
    }

    @Test
    void 행정동이_없는_매물은_구매자가_있어도_등록_때_배송비를_보여_준다() throws Exception {
        long noDongListingId = listings.getLast().id();

        mockMvc.perform(get("/api/listings/{listingId}", noDongListingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryFee").value(FIXED_FEE))
                .andExpect(jsonPath("$.totalPrice").value(PRICE + FIXED_FEE));
    }

    @Test
    void 상세도_구매자_행정동_기준_배송비를_보여_준다() throws Exception {
        Listing far = farListing();

        mockMvc.perform(get("/api/listings/{listingId}", far.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryFee").value(expectedFee(far)))
                .andExpect(jsonPath("$.totalPrice").value(PRICE + expectedFee(far)));
        mockMvc.perform(get("/api/listings/{listingId}", far.id()))
                .andExpect(jsonPath("$.deliveryFee").value(FIXED_FEE));
    }

    @Test
    void 찜_목록은_구매자_행정동_기준_배송비를_보여_준다() throws Exception {
        Listing far = farListing();
        jdbcTemplate.update("INSERT INTO favorites (member_id, listing_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP(6))",
                BUYER_ID, far.id());

        mockMvc.perform(get("/api/me/favorites").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(far.id()))
                .andExpect(jsonPath("$.items[0].deliveryFee").value(expectedFee(far)))
                .andExpect(jsonPath("$.items[0].totalPrice").value(PRICE + expectedFee(far)));
    }

    @Test
    void 배송비를_저장하지_않은_예전_주문은_등록_배송비를_보여_준다() throws Exception {
        Listing far = farListing();
        jdbcTemplate.update("""
                INSERT INTO orders (id, listing_id, buyer_id, delivery_status, order_status)
                VALUES (801, ?, ?, 'PENDING', 'CONFIRMED')
                """, far.id(), BUYER_ID);

        mockMvc.perform(get("/api/me/orders").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].listing.id").value(far.id()))
                .andExpect(jsonPath("$[0].listing.deliveryFee").value(FIXED_FEE));
        mockMvc.perform(get("/api/orders/{id}", 801).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listing.deliveryFee").value(FIXED_FEE));
    }

    @Test
    void 주문은_구매자_행정동_기준_배송비를_저장하고_결제_금액과_내_주문에_같은_값을_쓴다() throws Exception {
        Listing far = farListing();

        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":" + far.id() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryFee").value(expectedFee(far)))
                .andExpect(jsonPath("$.totalPrice").value(PRICE + expectedFee(far)))
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(body).get("id").asLong();

        assertThat(jdbcTemplate.queryForObject("SELECT delivery_dong_code FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo(BUYER_DONG);
        assertThat(paymentOrderReader.payableAmount(orderId)).isEqualTo(PRICE + expectedFee(far));
        mockMvc.perform(get("/api/me/orders").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID))
                .andExpect(jsonPath("$[0].listing.deliveryFee").value(expectedFee(far)));
        mockMvc.perform(get("/api/listings/{listingId}", far.id()).header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID))
                .andExpect(jsonPath("$.totalPrice").value(PRICE + expectedFee(far)));
    }

    @Test
    void 비로그인으로_본_상세_금액과_달라도_주문_응답은_승인_때_검증하는_금액을_준다() throws Exception {
        Listing far = farListing();
        mockMvc.perform(get("/api/listings/{listingId}", far.id()))
                .andExpect(jsonPath("$.totalPrice").value(PRICE + FIXED_FEE));

        String body = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":" + far.id() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body);

        assertThat(created.get("totalPrice").asInt())
                .isEqualTo(paymentOrderReader.payableAmount(created.get("id").asLong()))
                .isEqualTo(PRICE + expectedFee(far))
                .isNotEqualTo(PRICE + FIXED_FEE);
    }

    @Test
    void 목록_요청_1건은_커넥션을_한_번만_빌린다() throws Exception {
        long before = connectionAcquisitions();
        read(filtered(20_000).param("size", "20"));
        long afterFiltered = connectionAcquisitions();
        read(get("/api/listings").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID));

        assertThat(afterFiltered - before).isEqualTo(1);
        assertThat(connectionAcquisitions() - afterFiltered).isEqualTo(1);
    }

    @Test
    void 배송원_토큰으로는_배송비_필터를_쓸_수_없다() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO delivery_member (id, login_id, password, phone_number, license_plate_number, car_type,
                                             business_registration_number, token)
                VALUES (901, 'driver901', 'encoded-password', '010-0000-0000', '12가3456', '다마스', '000-00-00000', 'driver-token')
                """);

        mockMvc.perform(get("/api/listings")
                        .param("maxDeliveryFee", "20000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
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

    private int expectedFee(Listing listing) {
        if (listing.dongCode() == null) {
            return FIXED_FEE;
        }
        return deliveryFeeService.fee(
                deliveryFeeService.findDong(listing.dongCode()).orElseThrow().index(),
                deliveryFeeService.findDong(BUYER_DONG).orElseThrow().index(),
                listing.volumeTier());
    }

    // HikariCP가 커넥션을 내줄 때마다 기록하는 획득 시간 지표의 건수
    private long connectionAcquisitions() {
        return meterRegistry.get("hikaricp.connections.acquire").timer().count();
    }

    private Listing farListing() {
        return listings.stream()
                .filter(listing -> listing.dongCode() != null && expectedFee(listing) > FIXED_FEE)
                .findFirst().orElseThrow();
    }

    private Listing listingOf(long id) {
        return listings.stream().filter(listing -> listing.id() == id).findFirst().orElseThrow();
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
