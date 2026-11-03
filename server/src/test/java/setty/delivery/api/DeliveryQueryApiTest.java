package setty.delivery.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static setty.global.event.EventPublicationTestSupport.awaitEventsHandled;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import setty.platform.listing.storage.ListingImageStorage;
import setty.platform.DeliveryItemImageReader;
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

    @MockitoSpyBean
    private DeliveryItemImageReader deliveryItemImageReader;

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
    void 내_배차_페이지_목록과_상세에도_대표_사진을_제공한다() throws Exception {
        insertDelivery(1L, "ACCEPTED", 2L, BASE, BASE.plusSeconds(1));
        insertImage(1L, "shipment.jpg", 1);

        JsonNode list = read(get("/api/delivery/shipments").param("size", "20").param("group", "IN_PROGRESS"));
        JsonNode detail = read(get("/api/delivery/shipments/1"));

        assertThat(list.get("items").get(0).get("thumbnailUrl").asString()).isEqualTo(imageUrl("shipment.jpg"));
        assertThat(detail.get("furniture").get("thumbnailUrl").asString()).isEqualTo(imageUrl("shipment.jpg"));
    }

    @Test
    void 정산용_전체_조회는_배열_응답을_유지하고_사진_조회는_하지_않는다() throws Exception {
        insertDelivery(1L, "DELIVERED", 2L, BASE, BASE.plusSeconds(1));
        insertImage(1L, "unused-settlement.jpg", 1);

        final JsonNode list = read(get("/api/delivery/shipments"));

        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("deliveryFee").asLong()).isEqualTo(10_000);
        assertThat(list.get(0).hasNonNull("thumbnailUrl")).isFalse();
        verifyNoInteractions(deliveryItemImageReader);
    }

    @Test
    void 사진이_없는_기존_배송도_조회할_수_있다() throws Exception {
        insertDelivery(1L, "REQUESTED", null, BASE, null);

        assertThat(read(get("/api/delivery/requests")).get(0).hasNonNull("thumbnailUrl")).isFalse();
        assertThat(read(get("/api/delivery/requests/1")).get("furniture").hasNonNull("thumbnailUrl")).isFalse();
    }

    @Test
    void 예상_배송거리는_배송에_저장된_출발동에서_도착동까지_요청_목록과_상세에_제공한다() throws Exception {
        insertDelivery(1L, "REQUESTED", null, BASE, null);
        setRouteDongs(1L, "1111051500", "1111053000");

        assertEstimatedDistance(read(get("/api/delivery/requests")).get(0), 975);
        assertEstimatedDistance(read(get("/api/delivery/requests").param("size", "20")).get("items").get(0), 975);
        assertEstimatedDistance(read(get("/api/delivery/requests/1")), 975);
    }

    @Test
    void 예상_배송거리는_내_배차_페이지와_상세에도_방향을_지켜_제공한다() throws Exception {
        insertDelivery(1L, "ACCEPTED", 2L, BASE, BASE.plusSeconds(1));
        setRouteDongs(1L, "1111053000", "1111051500");

        assertEstimatedDistance(read(get("/api/delivery/shipments").param("size", "20")
                .param("group", "IN_PROGRESS")).get("items").get(0), 713);
        assertEstimatedDistance(read(get("/api/delivery/shipments/1")), 713);
        assertEstimatedDistance(read(get("/api/delivery/shipments")).get(0), null);
    }

    @Test
    void 예상_배송거리는_행정동이_없거나_서울_거리표_범위_밖이면_null이다() throws Exception {
        insertDelivery(1L, "REQUESTED", null, BASE, null);
        insertDelivery(2L, "ACCEPTED", 2L, BASE, BASE.plusSeconds(1));
        setRouteDongs(2L, "1111051500", "9999999999");

        assertEstimatedDistance(read(get("/api/delivery/requests").param("size", "20")).get("items").get(0), null);
        assertEstimatedDistance(read(get("/api/delivery/requests/1")), null);
        assertEstimatedDistance(read(get("/api/delivery/shipments").param("size", "20")
                .param("group", "IN_PROGRESS")).get("items").get(0), null);
        assertEstimatedDistance(read(get("/api/delivery/shipments/2")), null);
    }

    private void assertEstimatedDistance(final JsonNode response, final Integer expected) {
        final JsonNode distance = response.get("estimatedDistanceMeters");
        assertThat(distance).isNotNull();
        if (expected == null) {
            assertThat(distance.isNull()).isTrue();
        } else {
            assertThat(distance.asInt()).isEqualTo(expected);
        }
    }

    private void setRouteDongs(final long deliveryId, final String pickup, final String destination) {
        jdbcTemplate.update("UPDATE delivery SET pickup_dong_code = ?, delivery_dong_code = ? WHERE id = ?",
                pickup, destination, deliveryId);
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

    @Test
    void 같은_시각과_마이크로초_경계도_중복과_누락_없이_끝까지_읽는다() throws Exception {
        for (long id = 1; id <= 7; id++) {
            insertDelivery(id, "REQUESTED", null, BASE.plusNanos(id % 3 * 1_000), null);
        }
        final List<Long> received = new ArrayList<>();
        String cursor = null;
        do {
            final MockHttpServletRequestBuilder request = get("/api/delivery/requests").param("size", "3");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            final JsonNode page = read(request);
            received.addAll(ids(page.get("items")));
            cursor = nextCursor(page);
        } while (cursor != null);

        assertThat(received).containsExactly(5L, 2L, 7L, 4L, 1L, 6L, 3L);
    }

    @Test
    void 페이지에_해당하는_요청의_대표_사진만_제공한다() throws Exception {
        insertDelivery(1L, "REQUESTED", null, BASE, null);
        insertDelivery(2L, "REQUESTED", null, BASE.plusSeconds(1), null);
        insertImage(1L, "old.jpg", 1);
        insertImage(2L, "latest.jpg", 1);

        final JsonNode page = read(get("/api/delivery/requests").param("size", "1"));

        assertThat(ids(page.get("items"))).containsExactly(2L);
        assertThat(page.get("items").get(0).get("thumbnailUrl").asString()).isEqualTo(imageUrl("latest.jpg"));
        assertThat(nextCursor(page)).isNotNull();
    }

    @Test
    void 커서만_주면_기본_20건이고_옵션_없는_전체_조회는_집계를_위해_유지한다() throws Exception {
        for (long id = 1; id <= 25; id++) {
            insertDelivery(id, "REQUESTED", null, BASE.plusSeconds(id), null);
            insertDelivery(id + 100, "DELIVERED", 2L, BASE, BASE.plusSeconds(id));
        }
        final JsonNode first = read(get("/api/delivery/requests").param("size", "1"));
        final JsonNode next = read(get("/api/delivery/requests").param("cursor", nextCursor(first)));
        final JsonNode shipments = read(get("/api/delivery/shipments"));

        assertThat(next.get("items")).hasSize(20);
        assertThat(read(get("/api/delivery/requests"))).hasSize(25);
        assertThat(shipments).hasSize(25);
        long total = 0;
        for (JsonNode shipment : shipments) {
            total += shipment.get("deliveryFee").asLong();
        }
        assertThat(total).isEqualTo(250_000);
        assertThat(read(get("/api/delivery/shipments").param("group", "DELIVERED")).get("items")).hasSize(20);
    }

    @Test
    void 진행_중과_완료는_기사별로_필터링한_뒤_수락_시각으로_각각_페이징한다() throws Exception {
        insertDelivery(1L, "ACCEPTED", 2L, BASE.plusSeconds(10), BASE);
        insertDelivery(2L, "PICKED_UP", 2L, BASE, BASE.plusSeconds(1));
        insertDelivery(3L, "DELIVERED", 2L, BASE, BASE.plusSeconds(3));
        insertDelivery(4L, "DELIVERED", 2L, BASE, BASE.plusSeconds(2));
        insertDelivery(5L, "ACCEPTED", 3L, BASE, BASE.plusSeconds(9));
        insertDelivery(6L, "REQUESTED", null, BASE, null);
        insertImage(2L, "in-progress.jpg", 1);

        final JsonNode progress = read(get("/api/delivery/shipments").param("group", "IN_PROGRESS").param("size", "1"));
        final JsonNode progressNext = read(get("/api/delivery/shipments").param("group", "IN_PROGRESS")
                .param("size", "1").param("cursor", nextCursor(progress)));
        final JsonNode delivered = read(get("/api/delivery/shipments").param("group", "DELIVERED").param("size", "1"));
        final JsonNode deliveredNext = read(get("/api/delivery/shipments").param("group", "DELIVERED")
                .param("size", "1").param("cursor", nextCursor(delivered)));

        assertThat(ids(progress.get("items"))).containsExactly(2L);
        assertThat(progress.get("items").get(0).get("thumbnailUrl").asString()).isEqualTo(imageUrl("in-progress.jpg"));
        assertThat(ids(progressNext.get("items"))).containsExactly(1L);
        assertThat(nextCursor(progressNext)).isNull();
        assertThat(ids(delivered.get("items"))).containsExactly(3L);
        assertThat(ids(deliveredNext.get("items"))).containsExactly(4L);
        assertThat(nextCursor(deliveredNext)).isNull();
    }

    @Test
    void 페이지_사이의_수락은_제외하고_새_요청은_첫_페이지_갱신으로_받는다() throws Exception {
        for (long id = 1; id <= 5; id++) {
            insertDelivery(id, "REQUESTED", null, BASE.plusSeconds(id), null);
        }
        final JsonNode first = read(get("/api/delivery/requests").param("size", "2"));
        jdbcTemplate.update("UPDATE delivery SET status = 'ACCEPTED', driver_id = 3, accepted_at = ? WHERE id = 3",
                Timestamp.from(BASE.plusSeconds(6)));
        insertDelivery(6L, "REQUESTED", null, BASE.plusSeconds(6), null);

        assertThat(ids(read(get("/api/delivery/requests").param("size", "2")
                .param("cursor", nextCursor(first))).get("items"))).containsExactly(2L, 1L);
        assertThat(ids(read(get("/api/delivery/requests").param("size", "2")).get("items"))).containsExactly(6L, 5L);
    }

    @Test
    void 비어_있는_페이지는_다음_커서가_없다() throws Exception {
        for (String path : List.of("/api/delivery/requests", "/api/delivery/shipments")) {
            final JsonNode page = read(get(path).param("size", "20"));
            assertThat(page.get("items")).isEmpty();
            assertThat(nextCursor(page)).isNull();
        }
    }

    @Test
    void 잘못된_커서와_크기와_상태_그룹은_400이다() throws Exception {
        for (String path : List.of("/api/delivery/requests", "/api/delivery/shipments")) {
            for (String cursor : List.of("", "abc", "1", "-1_1", "1_0", "1_2_3")) {
                mockMvc.perform(get(path).param("cursor", cursor)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                        .andExpect(status().isBadRequest());
            }
            for (String size : List.of("0", "101", "-1", "abc")) {
                mockMvc.perform(get(path).param("size", size)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                        .andExpect(status().isBadRequest());
            }
            assertThat(read(get(path).param("size", "100")).get("items")).isEmpty();
            mockMvc.perform(get(path).param("size", "20")).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/delivery/shipments").param("group", "REQUESTED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer driver-token"))
                .andExpect(status().isBadRequest());
    }

    private List<Long> ids(final JsonNode items) {
        final List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("deliveryId").asLong()));
        return ids;
    }

    private String nextCursor(final JsonNode page) {
        return page.hasNonNull("nextCursor") ? page.get("nextCursor").asString() : null;
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
