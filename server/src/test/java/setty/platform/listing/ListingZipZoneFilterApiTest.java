package setty.platform.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import setty.deliveryfee.DeliveryFeeService;
import setty.deliveryfee.VolumeTier;
import setty.platform.listing.application.ListingCreateCommand;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingZipZoneBackfillJob;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.Dimensions;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.listing.storage.ListingImageStorage;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 우편번호 거리표는 구역 번호 차이 × 300m(판매자 번호가 크면 70m 더)인 합성 표다. 실제 거리표는 레포에 두지 않는다.
@SpringBootTest(properties = "setty.delivery-fee.zone-mode=zip")
@AutoConfigureMockMvc
class ListingZipZoneFilterApiTest extends MySqlIntegrationTestSupport {

    private static final Path TABLE_DIR = tempDirectory();
    private static final String TABLE_SHA256 = writeSyntheticTable(TABLE_DIR);

    private static final long SELLER_ID = 201L;
    private static final long ZIP_BUYER_ID = 202L;
    private static final long DONG_BUYER_ID = 203L;
    private static final String BUYER_DONG = "1111053000";
    private static final String BUYER_ZIP = "03186";
    private static final String SELLER_DONG = "1156054000";
    private static final int[] ZONE_OFFSETS = {0, 3, 7, 10, 11, 13, 20, 30, 45, -4, -12, -25};
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
    private ListingService listingService;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    private final List<Listing> listings = new ArrayList<>();
    private int buyerZone;

    // 해시가 맞는 파일이 폴더에 있으므로 S3에 묻지 않는다
    @DynamicPropertySource
    static void zipTable(final DynamicPropertyRegistry registry) {
        registry.add("setty.delivery-fee.zip-table.directory", TABLE_DIR::toString);
        registry.add("setty.delivery-fee.zip-table.sha256", () -> TABLE_SHA256);
        registry.add("setty.delivery-fee.zip-table.key", () -> "");
    }

    @AfterAll
    static void deleteTable() throws IOException {
        try (Stream<Path> files = Files.list(TABLE_DIR)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        Files.delete(TABLE_DIR);
    }

    @Test
    void 우편번호_거리표를_폴더의_파일로_켠다() {
        assertThat(deliveryFeeService.zipEnabled()).isTrue();
    }

    @BeforeEach
    void setUp() {
        cleanUp();
        buyerZone = deliveryFeeService.zipZoneOf(BUYER_ZIP).orElseThrow();
        insertMember(SELLER_ID, SELLER_DONG, null);
        insertMember(ZIP_BUYER_ID, BUYER_DONG, BUYER_ZIP);
        insertMember(DONG_BUYER_ID, BUYER_DONG, null);
        when(listingImageStorage.publicUrl(anyString()))
                .thenAnswer(invocation -> "https://images.example.test/" + invocation.getArgument(0));

        long id = 1;
        for (int offset : ZONE_OFFSETS) {
            for (VolumeTier volumeTier : VolumeTier.values()) {
                insertListing(new Listing(id, BASE.plusSeconds(id), SELLER_DONG, buyerZone + offset, volumeTier));
                id++;
            }
        }
        insertListing(new Listing(id, BASE.plusSeconds(id), SELLER_DONG, null, VolumeTier.SMALL));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM favorites");
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
        listings.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {10_000, 15_000, 20_000, 25_000, 40_000})
    void 우편번호_구역이_있는_구매자는_구역_범위로_거른_매물을_최신순으로_받는다(int maxDeliveryFee) throws Exception {
        JsonNode page = read(filtered(ZIP_BUYER_ID, maxDeliveryFee).param("size", "100"));

        assertThat(ids(page)).isNotEmpty().containsExactlyElementsOf(expectedZipIds(maxDeliveryFee));
        assertThat(page.get("items")).allSatisfy(item -> assertThat(item.get("deliveryFee").asInt())
                .isLessThanOrEqualTo(maxDeliveryFee)
                .isEqualTo(zipFee(listingOf(item.get("id").asLong()))));
    }

    @Test
    void 구역이_없는_매물은_우편번호_필터에서_빠진다() throws Exception {
        long noZoneListingId = listings.getLast().id();

        assertThat(ids(read(filtered(ZIP_BUYER_ID, 100_000).param("size", "100")))).doesNotContain(noZoneListingId);
    }

    @Test
    void 커서로_이어_받아도_예산_안의_매물을_빠짐없이_받는다() throws Exception {
        List<Long> received = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder request = filtered(ZIP_BUYER_ID, 20_000).param("size", "2");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode page = read(request);
            received.addAll(ids(page));
            cursor = page.has("nextCursor") ? page.get("nextCursor").asString() : null;
        } while (cursor != null);

        assertThat(received).containsExactlyElementsOf(expectedZipIds(20_000));
    }

    @Test
    void 우편번호가_없는_구매자는_행정동으로_거른다() throws Exception {
        // 판매자 동이 모두 같아 행정동 배송비는 부피로만 갈린다. 작은 매물 배송비를 예산으로 두면 작은 매물만 걸린다.
        int smallFee = dongFee(listings.getFirst());

        JsonNode page = read(filtered(DONG_BUYER_ID, smallFee).param("size", "100"));

        assertThat(ids(page)).containsExactlyElementsOf(listings.stream()
                .filter(listing -> dongFee(listing) <= smallFee)
                .sorted(Comparator.comparing(Listing::createdAt).reversed())
                .map(Listing::id)
                .toList());
        assertThat(ids(page)).contains(listings.getLast().id());
    }

    @Test
    void 필터_없이_보면_구역이_있는_매물은_우편번호로_없는_매물은_행정동으로_계산한다() throws Exception {
        JsonNode page = read(get("/api/listings").param("size", "100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + ZIP_BUYER_ID));

        assertThat(page.get("items")).hasSize(listings.size()).allSatisfy(item -> {
            Listing listing = listingOf(item.get("id").asLong());
            int expected = listing.zipZone() == null ? dongFee(listing) : zipFee(listing);
            assertThat(item.get("deliveryFee").asInt()).isEqualTo(expected);
        });
    }

    @Test
    void 상세와_주문은_우편번호_기준_배송비로_같은_금액을_쓴다() throws Exception {
        Listing far = listings.stream().filter(listing -> listing.zipZone() != null && zipFee(listing) != dongFee(listing))
                .findFirst().orElseThrow();

        mockMvc.perform(get("/api/listings/{listingId}", far.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + ZIP_BUYER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryFee").value(zipFee(far)));
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + ZIP_BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingId\":" + far.id() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryFee").value(zipFee(far)))
                .andExpect(jsonPath("$.totalPrice").value(PRICE + zipFee(far)));
    }

    @Test
    void 매물을_등록하면_판매자_우편번호_구역을_저장한다() {
        when(listingImageStorage.upload(anyList())).thenReturn(List.of("listings/test.jpg"));

        long inSeoul = create("01005");
        long outside = create("13529");
        long unresolved = create(null);

        assertThat(zipZoneOf(inSeoul)).isEqualTo(deliveryFeeService.zipZoneOf("01005").orElseThrow());
        assertThat(zipZoneOf(outside)).isNull();
        assertThat(zipZoneOf(unresolved)).isNull();
    }

    @Test
    void 구역이_빈_기존_매물을_판매자_우편번호로_채운다() {
        jdbcTemplate.update("UPDATE listings SET zip_zone = NULL");
        jdbcTemplate.update("UPDATE members SET zip_code = ? WHERE id = ?", "01005", SELLER_ID);
        insertMember(204L, SELLER_DONG, "13529");
        jdbcTemplate.update("UPDATE listings SET seller_id = 204 WHERE id = ?", listings.getFirst().id());

        int filled = new ListingZipZoneBackfillJob(jdbcTemplate, deliveryFeeService, 5, Duration.ZERO).run();

        assertThat(filled).isEqualTo(listings.size() - 1);
        assertThat(zipZoneOf(listings.getFirst().id())).isNull();
        assertThat(zipZoneOf(listings.getLast().id())).isEqualTo(deliveryFeeService.zipZoneOf("01005").orElseThrow());
        assertThat(new ListingZipZoneBackfillJob(jdbcTemplate, deliveryFeeService, 5, Duration.ZERO).run()).isZero();
    }

    private long create(String sellerZipCode) {
        return listingService.create(SELLER_ID, SELLER_DONG, sellerZipCode, new ListingCreateCommand(
                "우편번호 매물", "테스트 설명", PRICE, ListingCategory.DESK, ConditionGrade.A, Dimensions.of(10, 10, 10),
                List.of(new MockMultipartFile("images", "test.jpg", "image/jpeg", new byte[]{1}))
        )).listingId();
    }

    private Integer zipZoneOf(long listingId) {
        return jdbcTemplate.queryForObject("SELECT zip_zone FROM listings WHERE id = ?", Integer.class, listingId);
    }

    private int zipFee(Listing listing) {
        return deliveryFeeService.zipFee(listing.zipZone(), buyerZone, listing.volumeTier()).orElseThrow();
    }

    private int dongFee(Listing listing) {
        return deliveryFeeService.fee(
                deliveryFeeService.findDong(listing.dongCode()).orElseThrow().index(),
                deliveryFeeService.findDong(BUYER_DONG).orElseThrow().index(),
                listing.volumeTier());
    }

    private List<Long> expectedZipIds(int maxDeliveryFee) {
        return listings.stream()
                .filter(listing -> listing.zipZone() != null && zipFee(listing) <= maxDeliveryFee)
                .sorted(Comparator.comparing(Listing::createdAt).reversed())
                .map(Listing::id)
                .toList();
    }

    private Listing listingOf(long id) {
        return listings.stream().filter(listing -> listing.id() == id).findFirst().orElseThrow();
    }

    private MockHttpServletRequestBuilder filtered(long buyerId, int maxDeliveryFee) {
        return get("/api/listings")
                .param("maxDeliveryFee", String.valueOf(maxDeliveryFee))
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-" + buyerId);
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

    private void insertMember(long id, String dongCode, String zipCode) {
        jdbcTemplate.update("""
                INSERT INTO members (id, login_id, password, role, phone_number, address, dong_code, zip_code,
                                     address_status, token)
                VALUES (?, ?, 'encoded-password', 'MEMBER', '010-0000-0000', '가상 주소', ?, ?, ?, ?)
                """, id, "member" + id, dongCode, zipCode, zipCode == null ? "UNMATCHED" : "MATCHED", "token-" + id);
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
                INSERT INTO listings (id, seller_id, title, display_title, description, price, delivery_fee, category, condition_grade,
                                      width_cm, depth_cm, height_cm, sale_status, has_purchase_request,
                                      created_at, updated_at, dong_code, zip_zone)
                VALUES (?, ?, ?, ?, '테스트용 가상 매물입니다', 120000, 10000, 'CHAIR', 'A', 100, 100, ?, 'AVAILABLE', false, ?, ?, ?, ?)
                """, listing.id(), SELLER_ID, "가상 매물 " + listing.id(), "가상 매물 " + listing.id(), height, at, at, listing.dongCode(),
                listing.zipZone());
        listings.add(listing);
    }

    private static Path tempDirectory() {
        try {
            return Files.createTempDirectory("zip-table-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String writeSyntheticTable(Path directory) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path part = directory.resolve("synthetic.part");
            int size = 5_634;
            ByteBuffer row = ByteBuffer.allocate(size * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            try (FileChannel channel = FileChannel.open(part, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                for (int buyer = 0; buyer < size; buyer++) {
                    row.clear();
                    for (int seller = 0; seller < size; seller++) {
                        int tenMeters = Math.abs(seller - buyer) * 30 + (seller > buyer ? 7 : 0);
                        row.putShort((short) Math.min(65_535, tenMeters));
                    }
                    digest.update(row.array());
                    row.flip();
                    channel.write(row);
                }
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            Files.move(part, directory.resolve("seoul-zip-distances-" + sha256.substring(0, 12) + ".bin"));
            return sha256;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Listing(long id, Instant createdAt, String dongCode, Integer zipZone, VolumeTier volumeTier) {
    }
}
