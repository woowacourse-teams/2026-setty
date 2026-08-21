package setty.prototype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("프로토타입 쪽지 API")
class PrototypeListingMessageApiTest extends PrototypeApiSupport {
    private static final String MESSAGE_PAYLOAD = """
            {
              "content": "구매하고 싶습니다. 토요일 픽업 가능한가요?"
            }
            """;

    @Test
    @DisplayName("구매자는 로그인 없이 쪽지를 보낼 수 있다")
    void createsMessageWithoutLogin() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(session, "원목 책상");

        mockMvc.perform(post("/api/listings/" + listingId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MESSAGE_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("내용이 비면 쪽지를 보낼 수 없다")
    void rejectsBlankMessage() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(session, "원목 책상");

        mockMvc.perform(post("/api/listings/" + listingId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("없는 매물에는 쪽지를 보낼 수 없다")
    void rejectsMessageToUnknownListing() throws Exception {
        mockMvc.perform(post("/api/listings/999999/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MESSAGE_PAYLOAD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_FOUND"));
    }

    @Test
    @DisplayName("소유자는 받은 쪽지를 최신순으로 보고, 응답에 구매자 정보가 없다")
    void findsMessagesAsOwner() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(session, "원목 책상");
        sendMessage(listingId, "아직 판매 중인가요?");
        sendMessage(listingId, "구매하고 싶습니다.");

        final MvcResult result = mockMvc.perform(get("/api/listings/" + listingId + "/messages").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listingId").value(listingId))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].content").value("구매하고 싶습니다."))
                .andExpect(jsonPath("$.items[1].content").value("아직 판매 중인가요?"))
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
                .andReturn();

        assertThat(responseBodyOf(result)).doesNotContain("phoneNumber");
    }

    @Test
    @DisplayName("쪽지가 없으면 빈 목록을 돌려준다")
    void findsEmptyMessages() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(session, "원목 책상");

        mockMvc.perform(get("/api/listings/" + listingId + "/messages").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("다른 회원의 매물에 온 쪽지는 볼 수 없다")
    void rejectsMessagesForOtherMember() throws Exception {
        final MockHttpSession ownerSession = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(ownerSession, "원목 책상");
        sendMessage(listingId, "구매하고 싶습니다.");
        final MockHttpSession otherSession = logIn(OTHER_SELLER_PHONE_NUMBER);

        mockMvc.perform(get("/api/listings/" + listingId + "/messages").session(otherSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTING_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("로그인하지 않으면 쪽지를 볼 수 없다")
    void rejectsMessagesWithoutSession() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long listingId = createListing(session, "원목 책상");

        mockMvc.perform(get("/api/listings/" + listingId + "/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private void sendMessage(final long listingId, final String content) throws Exception {
        mockMvc.perform(post("/api/listings/" + listingId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "%s"
                                }
                                """.formatted(content)))
                .andExpect(status().isCreated());
    }
}
