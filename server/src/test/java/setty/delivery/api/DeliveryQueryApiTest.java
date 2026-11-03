package setty.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

import java.sql.Timestamp;
import java.time.Instant;
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
class DeliveryQueryApiTest extends MySqlIntegrationTestSupport {

    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00.123456Z");

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
                INSERT INTO members (id, login_id, password, role, phone_number, address)
                VALUES (1, 'delivery-query-owner', 'encoded', 'MEMBER', '010-0000-0000', '가상 주소')
                """);
        insertDriver(2L, "driver-token");
        insertDriver(3L, "other-token");
        when(listingImageStorage.publicUrl(anyString()))
                .thenAnswer(invocation -> "https://images.example.test/" + invocation.getArgument(0));
    }

    @AfterEach
    void cleanUp() {
        awaitEventsHandled(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM EVENT_PUBLICATION");
        jdbcTemplate.update("DELETE FROM delivery");
        jdbcTemplate.update("DELETE FROM delivery_order_decision");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
        jdbcTemplate.update("DELETE FROM delivery_member");
    }

    @Test
    void 요청_목록과_상세에_매물의_첫_사진을_제공한다() throws Exception {
        insertDelivery(1L, "REQUESTED", null, BASE, null);
        insertImage(1L, "second.jpg", 2);
        insertImage(1L, "first.jpg", 1);

        JsonNode list = read(get("/api/delivery/requests"));
        JsonNode detail = read(get("/api/delivery/requests/1"));

        assertThat(list.get(0).get("thumbnailUrl").asString()).isEqualTo(imageUrl("first.jpg"));
        assertThat(detail.get("furniture").get("thumbnailUrl").asString()).isEqualTo(imageUrl("first.jpg"));
    }

    @Test
    void 내_배차_목록과_상세에도_대표_사진을_제공한다() throws Exception {
        insertDelivery(1L, "ACCEPTED", 2L, BASE, BASE.plusSeconds(1));
        insertImage(1L, "shipment.jpg", 1);

        JsonNode list = read(get("/api/delivery/shipments"));
        JsonNode detail = read(get("/api/delivery/shipments/1"));

        assertThat(list.get(0).get("thumbnailUrl").asString()).isEqualTo(imageUrl("shipment.jpg"));
        assertThat(detail.get("furniture").get("thumbnailUrl").asString()).isEqualTo(imageUrl("shipment.jpg"));
    }

    @Test
    void 사진이_없는_기존_배송도_조회할_수_있다() throws Exception {
        insertDelivery(1L, "REQUESTED", null, BASE, null);

        assertThat(read(get("/api/delivery/requests")).get(0).hasNonNull("thumbnailUrl")).isFalse();
        assertThat(read(get("/api/delivery/requests/1")).get("furniture").hasNonNull("thumbnailUrl")).isFalse();
    }

    @Test
    void 인증되지_않았거나_다른_기사의_배차이면_사진도_조회할_수_없다() throws Exception {
        insertDelivery(1L, "ACCEPTED", 3L, BASE, BASE.plusSeconds(1));
        insertImage(1L, "private-shipment.jpg", 1);

        mockMvc.perform(get("/api/delivery/requests")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/delivery/shipments/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(status().isNotFound());
        assertThat(read(get("/api/delivery/shipments")).isEmpty()).isTrue();
    }

    private JsonNode read(final MockHttpServletRequestBuilder request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request
                        .header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void insertDriver(final long id, final String token) {
        jdbcTemplate.update("""
                INSERT INTO delivery_member (id, login_id, password, phone_number, license_plate_number, car_type,
                                             business_registration_number, token)
                VALUES (?, ?, 'encoded-password', '010-0000-0000', '12가3456', '다마스', '000-00-00000', ?)
                """, id, "query-driver-" + id, token);
    }

    private void insertDelivery(final long id, final String status, final Long driverId,
                                final Instant requestedAt, final Instant acceptedAt) {
        jdbcTemplate.update("""
                INSERT INTO listings (id, seller_id, title, description, price, delivery_fee, category, condition_grade,
                                      width_cm, depth_cm, height_cm, sale_status, has_purchase_request, created_at, updated_at)
                VALUES (?, 1, '가상 의자', '가상 설명', 10000, 10000, 'CHAIR', 'A', 10, 10, 10, 'RESERVED', true, ?, ?)
                """, id, Timestamp.from(requestedAt), Timestamp.from(requestedAt));
        jdbcTemplate.update("""
                INSERT INTO orders (id, listing_id, buyer_id, delivery_status, order_status)
                VALUES (?, ?, 1, ?, 'CONFIRMED')
                """, id, id, status);
        jdbcTemplate.update("""
                INSERT INTO delivery (id, order_id, driver_id, item_name, category, pickup_address, delivery_address,
                                      pickup_phone_number, delivery_phone_number, estimated_fee, status,
                                      requested_at, accepted_at)
                VALUES (?, ?, ?, '가상 의자', 'CHAIR', '가상 출발지', '가상 도착지',
                        '010-0000-0001', '010-0000-0002', 10000, ?, ?, ?)
                """, id, id, driverId, status, Timestamp.from(requestedAt),
                acceptedAt == null ? null : Timestamp.from(acceptedAt));
    }

    private void insertImage(final long listingId, final String filename, final int displayOrder) {
        jdbcTemplate.update("INSERT INTO listing_images (listing_id, object_key, display_order) VALUES (?, ?, ?)",
                listingId, "setty/images/listings/" + filename, displayOrder);
    }

    private String imageUrl(final String filename) {
        return "https://images.example.test/setty/images/listings/" + filename;
    }
}
