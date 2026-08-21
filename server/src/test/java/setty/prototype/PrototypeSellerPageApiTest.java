package setty.prototype;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

@DisplayName("프로토타입 판매자 마이페이지 API")
class PrototypeSellerPageApiTest extends PrototypeApiSupport {
    @Test
    @DisplayName("매물이 없으면 빈 목록과 0을 돌려준다")
    void findsEmptySellerPage() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);

        mockMvc.perform(get("/api/me/seller-page").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seller.phoneNumber").value("01000000001"))
                .andExpect(jsonPath("$.summary.listingCount").value(0))
                .andExpect(jsonPath("$.summary.messageCount").value(0))
                .andExpect(jsonPath("$.listings").isEmpty());
    }

    @Test
    @DisplayName("내 매물과 매물별로 받은 쪽지 수·최근 쪽지 시각을 돌려준다")
    void findsSellerPageWithMessageCounts() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        final long deskId = createListing(session, "원목 책상", 3);
        final long fridgeId = createListing(session, "소형 냉장고");
        sendMessage(deskId, "아직 판매 중인가요?");
        sendMessage(deskId, "토요일 픽업 가능한가요?");
        sendMessage(fridgeId, "구매하고 싶습니다.");

        mockMvc.perform(get("/api/me/seller-page").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.listingCount").value(2))
                .andExpect(jsonPath("$.summary.messageCount").value(3))
                .andExpect(jsonPath("$.listings[0].title").value("소형 냉장고"))
                .andExpect(jsonPath("$.listings[0].messageCount").value(1))
                .andExpect(jsonPath("$.listings[0].latestMessageAt").isNotEmpty())
                .andExpect(jsonPath("$.listings[1].title").value("원목 책상"))
                .andExpect(jsonPath("$.listings[1].messageCount").value(2))
                .andExpect(jsonPath("$.listings[1].thumbnailUrl").isNotEmpty());
    }

    @Test
    @DisplayName("쪽지를 받지 않은 매물은 쪽지 수가 0이고 최근 쪽지 시각이 없다")
    void findsListingWithoutMessage() throws Exception {
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        createListing(session, "원목 책상");

        mockMvc.perform(get("/api/me/seller-page").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings[0].messageCount").value(0))
                .andExpect(jsonPath("$.listings[0].latestMessageAt").doesNotExist());
    }

    @Test
    @DisplayName("다른 회원의 매물은 마이페이지에 담기지 않는다")
    void excludesOtherMemberListings() throws Exception {
        final MockHttpSession otherSession = logIn(OTHER_SELLER_PHONE_NUMBER);
        createListing(otherSession, "다른 회원 매물");
        final MockHttpSession session = logIn(SELLER_PHONE_NUMBER);
        createListing(session, "원목 책상");

        mockMvc.perform(get("/api/me/seller-page").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.listingCount").value(1))
                .andExpect(jsonPath("$.listings[0].title").value("원목 책상"));
    }

    @Test
    @DisplayName("로그인하지 않으면 마이페이지를 볼 수 없다")
    void rejectsWithoutSession() throws Exception {
        mockMvc.perform(get("/api/me/seller-page"))
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
