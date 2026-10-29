package setty.platform.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ListingGradeSearchApiTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 201L;
    private static final long BUYER_ID = 202L;
    private static final String DONG = "1111053000";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    private long nextId = 1;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertMember(SELLER_ID);
        insertMember(BUYER_ID);
        when(listingImageStorage.upload(anyList())).thenReturn(List.of("setty/images/listings/test.jpg"));
        when(listingImageStorage.publicUrl(anyString()))
                .thenAnswer(invocation -> "https://images.example.test/" + invocation.getArgument(0));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM favorites");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
        nextId = 1;
    }

    @Test
    void 등급은_하나_또는_여러_개로_거르고_비로그인도_쓸_수_있다() throws Exception {
        long s = insert("S급 의자", "", "CHAIR", "S");
        long a = insert("A급 의자", "", "CHAIR", "A");
        long b = insert("B급 의자", "", "CHAIR", "B");
        insert("C급 의자", "", "CHAIR", "C");

        assertThat(ids(read(get("/api/listings").param("conditionGrade", "S")))).containsExactly(s);
        assertThat(ids(read(get("/api/listings").param("conditionGrade", "S").param("conditionGrade", "B"))))
                .containsExactly(b, s);
        assertThat(ids(read(get("/api/listings").param("conditionGrade", "A,B")))).containsExactly(b, a);
    }

    @Test
    void 등급은_카테고리_검색어_배송비_필터와_함께_걸리고_커서로_이어_받아도_유지된다() throws Exception {
        long first = insert("원목 책상 1", "", "DESK", "S");
        long second = insert("원목 책상 2", "", "DESK", "S");
        long third = insert("원목 책상 3", "", "DESK", "S");
        insert("원목 책상 B급", "", "DESK", "B");
        insert("원목 테이블 S급", "", "TABLE", "S");
        insert("철제 책상 S급", "", "DESK", "S");

        List<Long> actual = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request = get("/api/listings").param("size", "2")
                    .param("conditionGrade", "S").param("category", "DESK").param("query", "원목")
                    .param("maxDeliveryFee", "40000")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID);
            if (cursor != null) request.param("cursor", cursor);
            JsonNode page = read(request);
            actual.addAll(ids(page));
            cursor = page.hasNonNull("nextCursor") ? page.get("nextCursor").asString() : null;
        } while (cursor != null);

        assertThat(actual).containsExactly(third, second, first);
    }

    @Test
    void 등급_필터는_삭제와_판매완료_매물을_빼고_모르는_등급은_거절한다() throws Exception {
        long deleted = insert("지운 의자", "", "CHAIR", "S");
        long sold = insert("팔린 의자", "", "CHAIR", "S");
        long available = insert("파는 의자", "", "CHAIR", "S");
        jdbcTemplate.update("UPDATE listings SET deleted_at = NOW(6) WHERE id = ?", deleted);
        jdbcTemplate.update("UPDATE listings SET sale_status = 'SOLD' WHERE id = ?", sold);

        assertThat(ids(read(get("/api/listings").param("conditionGrade", "S")))).containsExactly(available);
        mockMvc.perform(get("/api/listings").param("conditionGrade", "Z")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/listings").param("conditionGrade", "s")).andExpect(status().isBadRequest());
    }

    @Test
    void 검색어는_제목뿐_아니라_설명에서도_찾는다() throws Exception {
        long inTitle = insert("very nice chair", "편합니다", "CHAIR", "A");
        long inDescription = insert("사무용 가구", "회전되는 very nice chair 입니다", "CHAIR", "A");
        insert("원목 식탁", "4인용", "TABLE", "A");

        assertThat(ids(read(get("/api/listings").param("query", "Very Nice Chair"))))
                .containsExactly(inDescription, inTitle);
        assertThat(ids(read(get("/api/listings").param("query", "4인용")))).hasSize(1);
    }

    @Test
    void 동의어_묶음의_단어로_찾으면_묶음의_다른_단어와_카테고리_매물도_함께_나온다() throws Exception {
        long titled = insert("원목 테이블", "", "ETC", "A");
        long described = insert("거실 가구", "6인용 식탁입니다", "ETC", "A");
        long categorized = insert("북유럽 감성", "밝은 색", "TABLE", "A");
        insert("원목 책상", "", "DESK", "A");
        insert("컴퓨터 데스크", "", "ETC", "A");

        assertThat(ids(read(get("/api/listings").param("query", "탁자"))))
                .containsExactly(categorized, described, titled);
        assertThat(ids(read(get("/api/listings").param("query", "TABLE"))))
                .containsExactly(categorized, described, titled);
    }

    @Test
    void 묶음에_없는_검색어와_기타는_제목_설명_부분_일치만_본다() throws Exception {
        insert("북유럽 감성", "밝은 색", "TABLE", "A");
        long etc = insert("기타 가구", "", "ETC", "A");
        long lamp = insert("스탠드 조명", "기타 소품", "ETC", "A");
        insert("조명 없는 의자", "", "CHAIR", "A");

        assertThat(ids(read(get("/api/listings").param("query", "기타")))).containsExactly(lamp, etc);
        assertThat(ids(read(get("/api/listings").param("query", "밝은")))).hasSize(1);
    }

    @Test
    void 기타_카테고리로_등록하고_상세와_필터에서_본다() throws Exception {
        Map<String, Object> request = Map.of(
                "title", "거실 조명", "description", "스탠드형", "price", 30_000,
                "category", "ETC", "conditionGrade", "B",
                "dimensions", Map.of("widthCm", 30, "depthCm", 30, "heightCm", 150));
        JsonNode created = read(multipart("/api/listings")
                .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request)))
                .file(new MockMultipartFile("images", "lamp.jpg", MediaType.IMAGE_JPEG_VALUE, "fake".getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + SELLER_ID), 201);
        long listingId = created.get("listingId").asLong();
        insert("의자", "", "CHAIR", "A");

        mockMvc.perform(get("/api/listings/{id}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("ETC"));
        assertThat(ids(read(get("/api/listings").param("category", "ETC")))).containsExactly(listingId);
    }

    @Test
    void 가격은_하한_상한_범위로_거르고_다른_조건과_함께_걸린다() throws Exception {
        long cheap = insertPriced("의자 3만", "CHAIR", "S", 30_000);
        long mid = insertPriced("의자 10만", "CHAIR", "S", 100_000);
        long expensive = insertPriced("의자 25만", "CHAIR", "S", 250_000);
        long midButB = insertPriced("의자 10만 B급", "CHAIR", "B", 100_000);

        assertThat(ids(read(get("/api/listings").param("maxPrice", "100000")))).containsExactly(midButB, mid, cheap);
        assertThat(ids(read(get("/api/listings").param("minPrice", "100000")))).containsExactly(midButB, expensive, mid);
        assertThat(ids(read(get("/api/listings").param("minPrice", "50000").param("maxPrice", "200000"))))
                .containsExactly(midButB, mid);
        assertThat(ids(read(get("/api/listings").param("minPrice", "50000").param("maxPrice", "200000")
                .param("conditionGrade", "S").param("query", "의자")))).containsExactly(mid);
        assertThat(ids(read(get("/api/listings").param("maxPrice", "100000").param("size", "1")
                .param("maxDeliveryFee", "40000").header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID))))
                .containsExactly(midButB);
    }

    @Test
    void 가격_범위가_어긋나거나_등록_상한을_넘으면_거절한다() throws Exception {
        for (String[] params : List.of(new String[]{"minPrice", "-1"}, new String[]{"maxPrice", "100000001"},
                new String[]{"minPrice", "abc"})) {
            mockMvc.perform(get("/api/listings").param(params[0], params[1])).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/listings").param("minPrice", "200000").param("maxPrice", "100000"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/listings").param("minPrice", "0").param("maxPrice", "100000000"))
                .andExpect(status().isOk());
    }

    private long insertPriced(String title, String category, String grade, int price) {
        long id = insert(title, "", category, grade);
        jdbcTemplate.update("UPDATE listings SET price = ? WHERE id = ?", price, id);
        return id;
    }

    private long insert(String title, String description, String category, String grade) {
        long id = nextId++;
        Timestamp at = Timestamp.from(BASE.plusSeconds(id));
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category, condition_grade,
                                      width_cm, depth_cm, height_cm, sale_status, has_purchase_request,
                                      created_at, updated_at, dong_code)
                VALUES (?, ?, ?, ?, 120000, 10000, ?, ?, 100, 100, 30, 'AVAILABLE', false, ?, ?, ?)
                """, id, SELLER_ID, title, description, category, grade, at, at, DONG);
        return id;
    }

    private void insertMember(long id) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, dong_code, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?, ?)
                """, id, "member" + id, DONG, "token-" + id);
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        return read(request, 200);
    }

    private JsonNode read(RequestBuilder request, int expectedStatus) throws Exception {
        byte[] body = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsByteArray();
        return objectMapper.readTree(body);
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }
}
