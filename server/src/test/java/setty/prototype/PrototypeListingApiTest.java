package setty.prototype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("프로토타입 매물 API")
class PrototypeListingApiTest extends PrototypeApiSupport {
    private static final String LISTING_PAYLOAD = """
            {
              "title": "원목 책상",
              "description": "사용감이 조금 있습니다.",
              "pickupTimeText": "평일 오후 7시 이후",
              "canHelpMove": true
            }
            """;

    @Test
    @DisplayName("로그인한 판매자가 사진과 함께 매물을 올리면 매물 식별자와 위치를 돌려준다")
    void createsListing() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);

        final MvcResult result = mockMvc.perform(multipart("/api/listings")
                        .file(requestPart(LISTING_PAYLOAD))
                        .file(imagePart("item-1.jpg", "image/jpeg"))
                        .file(imagePart("item-2.png", "image/png"))
                        .session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingId").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(header().string("Location", Matchers.startsWith("/api/listings/")))
                .andReturn();

        final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(s3ObjectUploader, times(2))
                .upload(any(byte[].class), any(String.class), keyCaptor.capture());
        assertThat(keyCaptor.getAllValues()).allSatisfy(key ->
                assertThat(key).startsWith("setty/images/listings/"));
        assertThat(responseBodyOf(result)).doesNotContain("01000000001");
    }

    @Test
    @DisplayName("로그인하지 않으면 매물을 올릴 수 없고 사진도 저장하지 않는다")
    void rejectsCreateWithoutSession() throws Exception {
        mockMvc.perform(multipart("/api/listings")
                        .file(requestPart(LISTING_PAYLOAD))
                        .file(imagePart("item-1.jpg", "image/jpeg")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verify(s3ObjectUploader, never()).upload(any(byte[].class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("사진이 한 장도 없으면 매물을 올릴 수 없다")
    void rejectsListingWithoutImage() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);

        mockMvc.perform(multipart("/api/listings")
                        .file(requestPart(LISTING_PAYLOAD))
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE_COUNT"));
    }

    @Test
    @DisplayName("사진이 5장을 넘으면 매물을 올릴 수 없다")
    void rejectsTooManyImages() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);

        mockMvc.perform(multipart("/api/listings")
                        .file(requestPart(LISTING_PAYLOAD))
                        .file(imagePart("item-1.jpg", "image/jpeg"))
                        .file(imagePart("item-2.jpg", "image/jpeg"))
                        .file(imagePart("item-3.jpg", "image/jpeg"))
                        .file(imagePart("item-4.jpg", "image/jpeg"))
                        .file(imagePart("item-5.jpg", "image/jpeg"))
                        .file(imagePart("item-6.jpg", "image/jpeg"))
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE_COUNT"));

        verify(s3ObjectUploader, never()).upload(any(byte[].class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("지원하지 않는 형식의 파일은 사진으로 올릴 수 없다")
    void rejectsUnsupportedImageType() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);

        mockMvc.perform(multipart("/api/listings")
                        .file(requestPart(LISTING_PAYLOAD))
                        .file(imagePart("document.pdf", "application/pdf"))
                        .session(session))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));

        verify(s3ObjectUploader, never()).upload(any(byte[].class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("필수 값이 빠지면 매물을 올릴 수 없다")
    void rejectsMissingRequiredField() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);

        mockMvc.perform(multipart("/api/listings")
                        .file(requestPart("""
                                {
                                  "title": "원목 책상",
                                  "description": "사용감이 조금 있습니다.",
                                  "pickupTimeText": "평일 오후 7시 이후"
                                }
                                """))
                        .file(imagePart("item-1.jpg", "image/jpeg"))
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("매물 목록은 로그인 없이 최신 등록순으로 볼 수 있다")
    void findsAllListings() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        createListing(session, "원목 책상", 3);
        createListing(session, "소형 냉장고");

        final MvcResult result = mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("소형 냉장고"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl")
                        .value(Matchers.containsString("setty/images/listings/")))
                .andExpect(jsonPath("$.items[0].canHelpMove").value(true))
                .andExpect(jsonPath("$.items[1].title").value("원목 책상"))
                .andReturn();

        assertThat(responseBodyOf(result)).doesNotContain("01000000001");
    }

    @Test
    @DisplayName("등록된 매물이 없으면 빈 목록을 돌려준다")
    void findsEmptyListings() throws Exception {
        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("매물 상세는 사진을 순서대로 담고 판매자 연락처를 담지 않는다")
    void findsListingDetail() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(session, "원목 책상");

        final MvcResult result = mockMvc.perform(get("/api/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("원목 책상"))
                .andExpect(jsonPath("$.description").value("사용감이 조금 있습니다."))
                .andExpect(jsonPath("$.pickupTimeText").value("평일 오후 7시 이후"))
                .andExpect(jsonPath("$.images.length()").value(1))
                .andExpect(jsonPath("$.images[0].displayOrder").value(1))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        assertThat(responseBodyOf(result))
                .doesNotContain("01000000001")
                .doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("없는 매물을 조회하면 찾을 수 없다고 알린다")
    void rejectsUnknownListing() throws Exception {
        mockMvc.perform(get("/api/listings/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    @Test
    @DisplayName("소유자는 보낸 필드만 수정한다")
    void updatesOnlyGivenFields() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(session, "원목 책상");

        mockMvc.perform(patch("/api/listings/" + listingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "원목 책상 급처",
                                  "canHelpMove": false
                                }
                                """)
                        .session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("원목 책상 급처"))
                .andExpect(jsonPath("$.canHelpMove").value(false))
                .andExpect(jsonPath("$.description").value("사용감이 조금 있습니다."))
                .andExpect(jsonPath("$.pickupTimeText").value("평일 오후 7시 이후"));
    }

    @Test
    @DisplayName("변경할 값이 하나도 없으면 수정할 수 없다")
    void rejectsEmptyUpdate() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(session, "원목 책상");

        mockMvc.perform(patch("/api/listings/" + listingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("다른 회원의 매물은 수정할 수 없다")
    void rejectsUpdateByOtherMember() throws Exception {
        final MockHttpSession ownerSession = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(ownerSession, "원목 책상");
        final MockHttpSession otherSession = logIn(OTHER_SELLER_PHONE_NUMBER);

        mockMvc.perform(patch("/api/listings/" + listingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "가로챈 제목"
                                }
                                """)
                        .session(otherSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTING_ACCESS_DENIED"));

        mockMvc.perform(get("/api/listings/" + listingId))
                .andExpect(jsonPath("$.title").value("원목 책상"));
    }

    @Test
    @DisplayName("로그인하지 않으면 매물을 수정할 수 없다")
    void rejectsUpdateWithoutSession() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(session, "원목 책상");

        mockMvc.perform(patch("/api/listings/" + listingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "가로챈 제목"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("소유자가 매물을 삭제하면 더 이상 조회되지 않는다")
    void deletesListing() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(session, "원목 책상");

        mockMvc.perform(delete("/api/listings/" + listingId).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/listings/" + listingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
        mockMvc.perform(get("/api/listings"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("다른 회원의 매물은 삭제할 수 없다")
    void rejectsDeleteByOtherMember() throws Exception {
        final MockHttpSession ownerSession = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(ownerSession, "원목 책상");
        final MockHttpSession otherSession = logIn(OTHER_SELLER_PHONE_NUMBER);

        mockMvc.perform(delete("/api/listings/" + listingId).session(otherSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTING_ACCESS_DENIED"));

        mockMvc.perform(get("/api/listings/" + listingId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("사진 저장에 실패해도 계약 형식의 오류를 돌려준다")
    void handlesImageStorageFailure() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        doThrow(new RuntimeException("사진 저장 실패"))
                .when(s3ObjectUploader).upload(any(byte[].class), any(String.class), any(String.class));

        mockMvc.perform(multipart("/api/listings")
                        .file(requestPart(LISTING_PAYLOAD))
                        .file(imagePart("item-1.jpg", "image/jpeg"))
                        .session(session))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("요청을 처리하지 못했습니다."));
    }

    @Test
    @DisplayName("사진은 계약대로 JPEG·PNG·WebP만 올릴 수 있다")
    void storesSupportedImageTypes() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);

        mockMvc.perform(multipart("/api/listings")
                        .file(requestPart(LISTING_PAYLOAD))
                        .file(imagePart("item-1.webp", "image/webp"))
                        .session(session))
                .andExpect(status().isCreated());

        verify(s3ObjectUploader).upload(any(byte[].class), eq("image/webp"), any(String.class));
    }
}
