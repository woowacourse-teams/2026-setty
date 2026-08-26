package setty.platform.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.application.ListingView;
import setty.platform.listing.storage.ListingImageStorage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ListingApiTest {

    private static final long SELLER_ID = 101L;
    private static final long OTHER_SELLER_ID = 202L;
    private static final long FIRST_BUYER_ID = 303L;
    private static final long SECOND_BUYER_ID = 404L;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.6")
            .withDatabaseName("setty_test")
            .withUsername("setty_test")
            .withPassword("setty_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ListingService listingService;

    @MockitoBean
    private ListingImageStorage listingImageStorage;

    private final AtomicInteger objectKeySequence = new AtomicInteger();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM listing_images");
        jdbcTemplate.update("DELETE FROM listings");
        jdbcTemplate.update("DELETE FROM members");
        insertMember(SELLER_ID);
        insertMember(OTHER_SELLER_ID);
        insertMember(FIRST_BUYER_ID);
        insertMember(SECOND_BUYER_ID);

        objectKeySequence.set(0);
        reset(listingImageStorage);
        when(listingImageStorage.upload(anyList())).thenAnswer(invocation -> {
            List<MultipartFile> images = invocation.getArgument(0);
            return IntStream.range(0, images.size())
                    .mapToObj(ignored -> "listings/test-" + objectKeySequence.incrementAndGet() + ".jpg")
                    .toList();
        });
        when(listingImageStorage.publicUrl(anyString()))
                .thenAnswer(invocation -> "https://images.example.test/" + invocation.getArgument(0));
    }

    @Test
    void sellerCanCreateReadUpdateAndDeleteListing() throws Exception {
        long listingId = createListing(SELLER_ID, "가상 원목 의자");

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(listingId))
                .andExpect(jsonPath("$.items[0].title").value("가상 원목 의자"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl")
                        .value("https://images.example.test/listings/test-1.jpg"))
                .andExpect(jsonPath("$.items[0].price").value(120_000))
                .andExpect(jsonPath("$.items[0].deliveryFee").value(10_000))
                .andExpect(jsonPath("$.items[0].totalPrice").value(130_000));

        MvcResult detail = mockMvc.perform(get("/api/listings/{listingId}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("테스트용 가상 매물입니다"))
                .andExpect(jsonPath("$.category").value("CHAIR"))
                .andExpect(jsonPath("$.conditionGrade").value("A"))
                .andExpect(jsonPath("$.dimensions.widthCm").value(100))
                .andExpect(jsonPath("$.dimensions.depthCm").value(50))
                .andExpect(jsonPath("$.dimensions.heightCm").value(40))
                .andExpect(jsonPath("$.saleStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.images[0].displayOrder").value(1))
                .andReturn();
        long retainedImageId = read(detail).path("images").get(0).path("id").asLong();

        mockMvc.perform(get("/api/me/listings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(SELLER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(listingId))
                .andExpect(jsonPath("$.items[0].hasPurchaseRequest").value(false))
                .andExpect(jsonPath("$.items[0].canUpdate").value(true))
                .andExpect(jsonPath("$.items[0].canDelete").value(true));

        mockMvc.perform(updateRequest(
                        listingId,
                        SELLER_ID,
                        updateRequest("수정된 가상 책상", List.of(retainedImageId)),
                        imagePart("new-desk.jpg")
                ))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/listings/{listingId}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 가상 책상"))
                .andExpect(jsonPath("$.price").value(150_000))
                .andExpect(jsonPath("$.deliveryFee").value(30_000))
                .andExpect(jsonPath("$.totalPrice").value(180_000))
                .andExpect(jsonPath("$.category").value("DESK"))
                .andExpect(jsonPath("$.conditionGrade").value("B"))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[0].id").value(retainedImageId))
                .andExpect(jsonPath("$.images[0].url")
                        .value("https://images.example.test/listings/test-1.jpg"))
                .andExpect(jsonPath("$.images[1].url")
                        .value("https://images.example.test/listings/test-2.jpg"));

        mockMvc.perform(delete("/api/listings/{listingId}", listingId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(SELLER_ID)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/listings/{listingId}", listingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/me/listings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(SELLER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        verify(listingImageStorage).deleteAll(argThat(objectKeys ->
                objectKeys.size() == 2
                        && objectKeys.contains("listings/test-1.jpg")
                        && objectKeys.contains("listings/test-2.jpg")
        ));
    }

    @Test
    void requestWithoutBearerTokenReturnsUnauthorizedErrorContract() throws Exception {
        mockMvc.perform(get("/api/me/listings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다"));
    }

    @Test
    void requestWithInvalidBearerTokenReturnsUnauthorizedErrorContract() throws Exception {
        mockMvc.perform(get("/api/me/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다"));
    }

    @Test
    void createRequiresAtLeastOneImage() throws Exception {
        mockMvc.perform(multipart("/api/listings")
                        .file(jsonPart("request", createRequest("사진 없는 가상 매물")))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(SELLER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LISTING_IMAGE_COUNT"));
    }

    @Test
    void updateKeepsRetainedImageIdWhileRemovingAndReorderingImages() throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/listings")
                        .file(jsonPart("request", createRequest("사진 순서 확인용 가상 책상")))
                        .file(imagePart("first.jpg"))
                        .file(imagePart("second.jpg"))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(SELLER_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        long listingId = read(created).path("listingId").asLong();

        MvcResult beforeUpdate = mockMvc.perform(get("/api/listings/{listingId}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andReturn();
        long removedImageId = read(beforeUpdate).path("images").get(0).path("id").asLong();
        long retainedImageId = read(beforeUpdate).path("images").get(1).path("id").asLong();

        mockMvc.perform(updateRequest(
                        listingId,
                        SELLER_ID,
                        updateRequest("사진이 수정된 가상 책상", List.of(retainedImageId)),
                        imagePart("replacement.jpg")
                ))
                .andExpect(status().isNoContent());

        MvcResult afterUpdate = mockMvc.perform(get("/api/listings/{listingId}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[0].id").value(retainedImageId))
                .andExpect(jsonPath("$.images[0].url")
                        .value("https://images.example.test/listings/test-2.jpg"))
                .andExpect(jsonPath("$.images[1].url")
                        .value("https://images.example.test/listings/test-3.jpg"))
                .andReturn();
        long newImageId = read(afterUpdate).path("images").get(1).path("id").asLong();
        assertThat(newImageId).isNotIn(removedImageId, retainedImageId);

        verify(listingImageStorage).deleteAll(argThat(objectKeys ->
                objectKeys.size() == 1 && objectKeys.contains("listings/test-1.jpg")
        ));
    }

    @Test
    void differentSellerCannotModifyOrDeleteListing() throws Exception {
        long listingId = createListing(SELLER_ID, "소유권 확인용 가상 수납장");
        long retainedImageId = firstImageId(listingId);

        mockMvc.perform(updateRequest(
                        listingId,
                        OTHER_SELLER_ID,
                        updateRequest("다른 판매자의 수정 시도", List.of(retainedImageId))
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));

        mockMvc.perform(delete("/api/listings/{listingId}", listingId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(OTHER_SELLER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));

        mockMvc.perform(get("/api/listings/{listingId}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("소유권 확인용 가상 수납장"));
    }

    @Test
    void purchaseRequestBlocksMutationAndOnlyFirstReservationWinsUntilSaleCompletes() throws Exception {
        long listingId = createListing(SELLER_ID, "구매 흐름 확인용 가상 소파");
        long retainedImageId = firstImageId(listingId);

        ListingView.PurchaseInfo firstPurchase = listingService.registerPurchaseRequest(listingId, FIRST_BUYER_ID);

        assertThat(firstPurchase.listingId()).isEqualTo(listingId);
        assertThat(firstPurchase.sellerId()).isEqualTo(SELLER_ID);
        assertThat(firstPurchase.totalPrice()).isEqualTo(130_000);
        assertThatThrownBy(() -> listingService.registerPurchaseRequest(listingId, SECOND_BUYER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_ORDERED)
                );

        mockMvc.perform(updateRequest(
                        listingId,
                        SELLER_ID,
                        updateRequest("구매 신청 후 수정 시도", List.of(retainedImageId))
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LISTING_UPDATE_NOT_ALLOWED"));

        mockMvc.perform(delete("/api/listings/{listingId}", listingId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(SELLER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LISTING_DELETE_NOT_ALLOWED"));

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(listingId));
        mockMvc.perform(get("/api/me/listings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(SELLER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].hasPurchaseRequest").value(true))
                .andExpect(jsonPath("$.items[0].canUpdate").value(false))
                .andExpect(jsonPath("$.items[0].canDelete").value(false));

        assertThat(listingService.reserveForDelivery(listingId)).isTrue();
        assertThat(listingService.reserveForDelivery(listingId)).isFalse();
        listingService.completeSale(listingId);

        mockMvc.perform(get("/api/listings/{listingId}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saleStatus").value("SOLD"));
        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/me/listings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(SELLER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].saleStatus").value("SOLD"));
    }

    @Test
    void concurrentDeliveryReservationsAllowOnlyOneWinner() throws Exception {
        long listingId = createListing(SELLER_ID, "동시 예약 확인용 가상 침대");
        listingService.registerPurchaseRequest(listingId, FIRST_BUYER_ID);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstReservation = executor.submit(() -> reserveAfterSignal(listingId, ready, start));
            Future<Boolean> secondReservation = executor.submit(() -> reserveAfterSignal(listingId, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    firstReservation.get(10, TimeUnit.SECONDS),
                    secondReservation.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void concurrentPurchaseRequestsAllowOnlyOneOrderCandidate() throws Exception {
        long listingId = createListing(SELLER_ID, "동시 구매 신청 확인용 가상 테이블");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstPurchase = executor.submit(
                    () -> registerPurchaseAfterSignal(listingId, FIRST_BUYER_ID, ready, start)
            );
            Future<Boolean> secondPurchase = executor.submit(
                    () -> registerPurchaseAfterSignal(listingId, SECOND_BUYER_ID, ready, start)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    firstPurchase.get(10, TimeUnit.SECONDS),
                    secondPurchase.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(true, false);
        }
    }

    private long createListing(long sellerId, String title) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/listings")
                        .file(jsonPart("request", createRequest(title)))
                        .file(imagePart("furniture.jpg"))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(sellerId)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.listingId").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        long listingId = read(result).path("listingId").asLong();
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/listings/" + listingId);
        return listingId;
    }

    private boolean reserveAfterSignal(
            long listingId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("가상 동시 예약 테스트 시작 신호를 받지 못했습니다");
        }
        return listingService.reserveForDelivery(listingId);
    }

    private boolean registerPurchaseAfterSignal(
            long listingId,
            long buyerId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("가상 동시 구매 신청 테스트 시작 신호를 받지 못했습니다");
        }
        try {
            listingService.registerPurchaseRequest(listingId, buyerId);
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_ORDERED);
            return false;
        }
    }

    private long firstImageId(long listingId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/listings/{listingId}", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andReturn();
        return read(result).path("images").get(0).path("id").asLong();
    }

    private MockMultipartHttpServletRequestBuilder updateRequest(
            long listingId,
            long sellerId,
            Map<String, Object> request,
            MockMultipartFile... images
    ) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/listings/{listingId}", listingId)
                .file(jsonPart("request", request))
                .header(HttpHeaders.AUTHORIZATION, bearerToken(sellerId))
                .with(servletRequest -> {
                    servletRequest.setMethod("PUT");
                    return servletRequest;
                });
        for (MockMultipartFile image : images) {
            builder.file(image);
        }
        return builder;
    }

    private MockMultipartFile jsonPart(String name, Object value) throws Exception {
        return new MockMultipartFile(
                name,
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(value)
        );
    }

    private MockMultipartFile imagePart(String filename) {
        return new MockMultipartFile(
                "images",
                filename,
                MediaType.IMAGE_JPEG_VALUE,
                "fake-jpeg-image".getBytes(StandardCharsets.UTF_8)
        );
    }

    private Map<String, Object> createRequest(String title) {
        return Map.of(
                "title", title,
                "description", "테스트용 가상 매물입니다",
                "price", 120_000,
                "category", "CHAIR",
                "conditionGrade", "A",
                "dimensions", Map.of(
                        "widthCm", 100,
                        "depthCm", 50,
                        "heightCm", 40
                )
        );
    }

    private Map<String, Object> updateRequest(String title, List<Long> retainedImageIds) {
        return Map.of(
                "title", title,
                "description", "수정된 테스트용 가상 매물입니다",
                "price", 150_000,
                "category", "DESK",
                "conditionGrade", "B",
                "dimensions", Map.of(
                        "widthCm", 200,
                        "depthCm", 100,
                        "heightCm", 100
                ),
                "retainedImageIds", retainedImageIds
        );
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void insertMember(long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO members (id, login_id, password, role, phone_number, address, token)
                VALUES (?, ?, ?, 'PLATFORM', '010-0000-0000', '가상 주소', ?)
                """,
                memberId,
                "member" + memberId,
                "encoded-password",
                token(memberId)
        );
    }

    private static String bearerToken(long memberId) {
        return "Bearer " + token(memberId);
    }

    private static String token(long memberId) {
        return "token-" + memberId;
    }
}
