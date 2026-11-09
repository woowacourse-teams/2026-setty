package setty.platform.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SimilarListingApiTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 301L;
    private static final long BUYER_ID = 302L;
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
    void 같은_카테고리에서_제목이_많이_겹치는_순으로_주고_같으면_가격이_가까운_순이다() throws Exception {
        long target = insert("원목 식탁 4인용", "TABLE", 120_000, SELLER_ID);
        long farPrice = insert("원목 식탁", "TABLE", 200_000, SELLER_ID);
        long nearPrice = insert("원목 식탁", "TABLE", 130_000, SELLER_ID);
        long closestTitle = insert("4인 원목식탁", "TABLE", 120_000, SELLER_ID);
        long unrelated = insert("철제 테이블", "TABLE", 120_000, SELLER_ID);
        insert("원목 책상", "DESK", 120_000, SELLER_ID);

        assertThat(ids(read(get("/api/listings/{id}/similar", target))))
                .containsExactly(closestTitle, nearPrice, farPrice, unrelated);
    }

    @Test
    void 겹치는_글자가_없어도_같은_카테고리_매물로_자리를_채운다() throws Exception {
        long target = insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        long near = insert("철제 좌탁", "TABLE", 110_000, SELLER_ID);
        long far = insert("유리 협탁", "TABLE", 50_000, SELLER_ID);

        assertThat(ids(read(get("/api/listings/{id}/similar", target)))).containsExactly(near, far);
    }

    @Test
    void 지금_살_수_없는_매물은_빼고_판매가_끝난_매물에서도_추천을_본다() throws Exception {
        long target = insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        long available = insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        long sold = insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        long deleted = insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        long requested = insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        jdbcTemplate.update("UPDATE listings SET sale_status = 'SOLD' WHERE id IN (?, ?)", sold, target);
        jdbcTemplate.update("UPDATE listings SET deleted_at = NOW(6) WHERE id = ?", deleted);
        jdbcTemplate.update("UPDATE listings SET has_purchase_request = true WHERE id = ?", requested);

        assertThat(ids(read(get("/api/listings/{id}/similar", target)))).containsExactly(available);
    }

    @Test
    void 조회자_본인_매물은_빼고_구매자_기준_총액을_준다() throws Exception {
        long target = insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        long others = insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        insert("원목 식탁", "TABLE", 120_000, BUYER_ID);

        JsonNode response = read(get("/api/listings/{id}/similar", target)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + BUYER_ID));

        assertThat(ids(response)).containsExactly(others);
        JsonNode item = response.get("items").get(0);
        assertThat(item.get("totalPrice").asInt()).isEqualTo(item.get("price").asInt() + item.get("deliveryFee").asInt());
        assertThat(ids(read(get("/api/listings/{id}/similar", target)))).hasSize(2);
    }

    @Test
    void 최대_여섯_개까지_준다() throws Exception {
        long target = insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        for (int count = 0; count < 8; count++) {
            insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        }

        assertThat(ids(read(get("/api/listings/{id}/similar", target)))).hasSize(6);
    }

    @Test
    void 기타_카테고리는_빈_목록이다() throws Exception {
        long target = insert("스탠드 조명", "ETC", 30_000, SELLER_ID);
        insert("스탠드 조명", "ETC", 30_000, SELLER_ID);

        assertThat(ids(read(get("/api/listings/{id}/similar", target)))).isEmpty();
    }

    @Test
    void 없거나_지운_매물은_찾을_수_없다() throws Exception {
        long deleted = insert("원목 식탁", "TABLE", 120_000, SELLER_ID);
        jdbcTemplate.update("UPDATE listings SET deleted_at = NOW(6) WHERE id = ?", deleted);

        mockMvc.perform(get("/api/listings/{id}/similar", deleted)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/listings/{id}/similar", 9_999)).andExpect(status().isNotFound());
    }

    private long insert(String title, String category, int price, long sellerId) {
        long id = nextId++;
        Timestamp at = Timestamp.from(BASE.plusSeconds(id));
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category, condition_grade,
                                      width_cm, depth_cm, height_cm, sale_status, has_purchase_request,
                                      created_at, updated_at, dong_code)
                VALUES (?, ?, ?, '', ?, 10000, ?, 'A', 100, 100, 30, 'AVAILABLE', false, ?, ?, ?)
                """, id, sellerId, title, price, category, at, at, DONG);
        return id;
    }

    private void insertMember(long id) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, dong_code, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?, ?)
                """, id, "member" + id, DONG, "token-" + id);
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        byte[] body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return objectMapper.readTree(body);
    }

    private List<Long> ids(JsonNode response) {
        List<Long> ids = new ArrayList<>();
        response.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }
}
