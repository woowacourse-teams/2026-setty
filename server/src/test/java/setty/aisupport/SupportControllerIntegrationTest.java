package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static setty.aisupport.SupportDtos.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SupportControllerIntegrationTest extends MySqlIntegrationTestSupport {
    static final long MEMBER=910001L;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired SupportStore store;
    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO members(id,login_id,password,role,phone_number,address,dong_code,token,token_expires_at) VALUES(?,?,'encoded','MEMBER','010-0000-0000','가상주소','1111053000',?,TIMESTAMPADD(DAY, 30, UTC_TIMESTAMP(6)))",MEMBER,"support-one","support-token");
        jdbc.update("INSERT INTO members(id,login_id,password,role,phone_number,address,dong_code,token,token_expires_at) VALUES(?,?,'encoded','MEMBER','010-0000-0000','가상주소','1111053000',?,TIMESTAMPADD(DAY, 30, UTC_TIMESTAMP(6)))",MEMBER+1,"support-two","other-support-token");
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM ai_support_cancellation WHERE member_id IN (?,?)",MEMBER,MEMBER+1);
        jdbc.update("DELETE FROM ai_support_conversation WHERE member_id IN (?,?)",MEMBER,MEMBER+1);
        jdbc.update("DELETE FROM ai_support_daily_usage WHERE member_id IN (?,?)",MEMBER,MEMBER+1);
        jdbc.update("DELETE FROM members WHERE id IN (?,?)",MEMBER,MEMBER+1);
    }
    @Test void pendingCancellationCanBeReadWithAiDisabledAndOnlyByItsOwner() throws Exception {
        jdbc.update("INSERT INTO ai_support_cancellation(request_id,member_id,order_id,question_turn_id,consent_turn_id,consent_method,policy_version,status,accepted_at) VALUES('pending-http',?,101,1,2,'BUTTON','p1','ACCEPTED',UTC_TIMESTAMP(6))",MEMBER);
        mvc.perform(get("/api/support/cancellations/pending-http")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/support/cancellations/pending-http").header("Authorization","Bearer other-support-token"))
                .andExpect(status().isNotFound());
        String response=mvc.perform(get("/api/support/cancellations/pending-http").header("Authorization","Bearer support-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptedAt").isString()).andReturn().getResponse().getContentAsString();
        var body=json.readTree(response);
        assertThat(body.has("decidedAt")).isTrue();
        assertThat(body.get("decidedAt").isNull()).isTrue();
        assertThat(body.has("reasonCode")).isTrue();
        assertThat(body.get("reasonCode").isNull()).isTrue();
    }
    @Test void authenticationOwnershipDisabledAndHistoryEndpointsUseExpectedJson() throws Exception {
        mvc.perform(get("/api/support/conversations")).andExpect(status().isUnauthorized());
        String created=mvc.perform(post("/api/support/conversations").header("Authorization","Bearer support-token"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("새 상담"))
                .andExpect(jsonPath("$.expiresAt").isString()).andReturn().getResponse().getContentAsString();
        long id=json.readTree(created).get("id").asLong();
        mvc.perform(get("/api/support/conversations/"+id).header("Authorization","Bearer other-support-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        mvc.perform(post("/api/support/conversations/"+id+"/messages").header("Authorization","Bearer support-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"clientRequestId\":\"a\",\"input\":{\"type\":\"TEXT\",\"text\":\"취소문의\"}}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("AI_DISABLED"));
        mvc.perform(get("/api/support/conversations/"+id).header("Authorization","Bearer support-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.activeTurn").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(delete("/api/support/conversations/"+id).header("Authorization","Bearer support-token"))
                .andExpect(status().isNoContent());
    }
    @Test void persistedTurnCardsAndDuplicateSerializeNullableFieldsWithoutModelCall() throws Exception {
        long id=store.create(MEMBER).id();
        SendRequest request=new SendRequest("known-request",new Input(SupportDtos.InputKind.TEXT,"주문 찾아줘",null,null));
        Turn turn=store.accept(MEMBER,id,request).turn();
        Map<String,Object> candidate=new LinkedHashMap<>();
        candidate.put("orderId",12);candidate.put("title",null);candidate.put("createdAt",null);candidate.put("orderStatus","CONFIRMED");
        Map<String,Object> card=new LinkedHashMap<>();
        card.put("type","ORDERS");card.put("orders",List.of(candidate));card.put("nextCursor",null);card.put("hasUndatedOrders",true);
        store.complete(MEMBER,id,turn.turnId(),new Result("선택해 주세요",List.of(card),0,0,List.of()),null,"fixture-not-live-model",1);
        String response=mvc.perform(post("/api/support/conversations/"+id+"/messages").header("Authorization","Bearer support-token")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.turnId").value(turn.turnId()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.input.type").value("TEXT"))
                .andExpect(jsonPath("$.cards[0].orders[0].orderId").value(12))
                .andReturn().getResponse().getContentAsString();
        var body=json.readTree(response);
        assertThat(body.get("cards").get(0).get("orders").get(0).get("createdAt").isNull()).isTrue();
        assertThat(body.get("cards").get(0).get("orders").get(0).get("title").isNull()).isTrue();
        assertThat(body.get("errorCode").isNull()).isTrue();
        mvc.perform(get("/api/support/conversations/"+id).header("Authorization","Bearer support-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].turnId").value(turn.turnId()))
                .andExpect(jsonPath("$.conversation.id").value(id));
    }
}
