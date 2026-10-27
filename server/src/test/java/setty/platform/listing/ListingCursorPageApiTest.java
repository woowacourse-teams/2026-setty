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
class ListingCursorPageApiTest extends MySqlIntegrationTestSupport {

    private static final long SELLER_ID = 101L;
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00.123456Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, 'seller', 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', NULL)
                """, SELLER_ID);
        when(listingImageStorage.publicUrl(anyString()))
                .thenAnswer(invocation -> "https://images.example.test/" + invocation.getArgument(0));
    }

    // 다른 테스트가 members를 지울 때 이 테스트의 매물이 외래 키로 막지 않게 한다
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
    }

    @Test
    void 첫_페이지는_최신순으로_요청한_개수만큼_주고_다음_커서를_준다() throws Exception {
        for (long id = 1; id <= 3; id++) {
            insertListing(id, BASE.plusSeconds(id), "AVAILABLE", false);
        }

        JsonNode page = read(get("/api/listings").param("size", "2"));

        assertThat(ids(page)).containsExactly(3L, 2L);
        assertThat(nextCursor(page)).isNotNull();
    }

    @Test
    void 다음_커서로_이어_받으면_중복과_누락_없이_끝까지_받는다() throws Exception {
        for (long id = 1; id <= 7; id++) {
            insertListing(id, BASE.plusSeconds(id), "AVAILABLE", false);
        }

        List<Long> received = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletRequestBuilder request = get("/api/listings").param("size", "3");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = read(request);
            received.addAll(ids(page));
            cursor = nextCursor(page);
            pages++;
        } while (cursor != null);

        assertThat(received).containsExactly(7L, 6L, 5L, 4L, 3L, 2L, 1L);
        assertThat(pages).isEqualTo(3);
    }

    @Test
    void 등록_시각이_같으면_id로_경계를_나눈다() throws Exception {
        for (long id = 1; id <= 3; id++) {
            insertListing(id, BASE, "AVAILABLE", false);
        }

        JsonNode first = read(get("/api/listings").param("size", "2"));
        JsonNode second = read(get("/api/listings").param("size", "2")
                .param("cursor", nextCursor(first)));

        assertThat(ids(first)).containsExactly(3L, 2L);
        assertThat(ids(second)).containsExactly(1L);
        assertThat(nextCursor(second)).isNull();
    }

    @Test
    void 판매_중이_아닌_매물은_목록에_없다() throws Exception {
        insertListing(1L, BASE.plusSeconds(1), "AVAILABLE", false);
        insertListing(2L, BASE.plusSeconds(2), "RESERVED", false);
        insertListing(3L, BASE.plusSeconds(3), "SOLD", false);
        insertListing(4L, BASE.plusSeconds(4), "AVAILABLE", true);

        assertThat(ids(read(get("/api/listings").param("size", "20")))).containsExactly(1L);
        assertThat(ids(read(get("/api/listings")))).containsExactly(1L);
    }

    @Test
    void 대표_사진은_해당_페이지_매물의_첫_사진이다() throws Exception {
        insertListing(1L, BASE.plusSeconds(1), "AVAILABLE", false);
        insertListing(2L, BASE.plusSeconds(2), "AVAILABLE", false);
        insertImage(1L, "setty/images/listings/one.jpg", 1);
        insertImage(2L, "setty/images/listings/two-first.jpg", 1);
        insertImage(2L, "setty/images/listings/two-second.jpg", 2);

        JsonNode page = read(get("/api/listings").param("size", "1"));

        assertThat(ids(page)).containsExactly(2L);
        assertThat(page.get("items").get(0).get("thumbnailUrl").asString())
                .isEqualTo("https://images.example.test/setty/images/listings/two-first.jpg");
    }

    @Test
    void 커서와_크기_없이_부르면_기존처럼_전체를_준다() throws Exception {
        for (long id = 1; id <= 25; id++) {
            insertListing(id, BASE.plusSeconds(id), "AVAILABLE", false);
        }

        JsonNode page = read(get("/api/listings"));

        assertThat(page.get("items")).hasSize(25);
        assertThat(page.has("nextCursor")).isFalse();
    }

    @Test
    void 커서만_주면_기본_20개씩_준다() throws Exception {
        for (long id = 1; id <= 25; id++) {
            insertListing(id, BASE.plusSeconds(id), "AVAILABLE", false);
        }
        JsonNode first = read(get("/api/listings").param("size", "1"));

        JsonNode page = read(get("/api/listings").param("cursor", nextCursor(first)));

        assertThat(page.get("items")).hasSize(20);
        assertThat(ids(page).getFirst()).isEqualTo(24L);
    }

    @Test
    void 페이지_크기는_1부터_100까지다() throws Exception {
        insertListing(1L, BASE, "AVAILABLE", false);

        mockMvc.perform(get("/api/listings").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/listings").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/listings").param("size", "100")).andExpect(status().isOk());
    }

    @Test
    void 형식이_잘못된_커서는_거부한다() throws Exception {
        mockMvc.perform(get("/api/listings").param("cursor", "abc")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/listings").param("cursor", "123")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/listings").param("cursor", "123_0")).andExpect(status().isBadRequest());
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        byte[] body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        return objectMapper.readTree(body);
    }

    private static String nextCursor(JsonNode page) {
        return page.has("nextCursor") ? page.get("nextCursor").asString() : null;
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private void insertListing(long id, Instant createdAt, String saleStatus, boolean deleted) {
        Timestamp at = Timestamp.from(createdAt);
        jdbcTemplate.update(
                """
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category, condition_grade,
                                      width_cm, depth_cm, height_cm, sale_status, has_purchase_request,
                                      created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, '테스트용 가상 매물입니다', 120000, 10000, 'CHAIR', 'A', 50, 50, 80, ?, false, ?, ?, ?)
                """,
                id, SELLER_ID, "가상 매물 " + id, saleStatus, at, at, deleted ? at : null
        );
    }

    private void insertImage(long listingId, String objectKey, int displayOrder) {
        jdbcTemplate.update(
                "INSERT INTO listing_images (listing_id, object_key, display_order) VALUES (?, ?, ?)",
                listingId, objectKey, displayOrder
        );
    }
}
