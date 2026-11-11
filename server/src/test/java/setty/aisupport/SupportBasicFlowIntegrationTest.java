package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static setty.aisupport.SupportDtos.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import setty.platform.OrderSupportReader;
import setty.platform.OrderSupportCancellation;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

/** T01: real persistence/orchestration; model and order gateway are controlled independently. */
@SpringBootTest
class SupportBasicFlowIntegrationTest extends MySqlIntegrationTestSupport {
    static final Instant NOW=Instant.parse("2026-11-06T00:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    SupportStore store;
    SupportService service;
    SupportModel model;
    OrderSupportReader reader;
    long conversation;
    Clock clock;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM ai_support_conversation");
        jdbc.update("DELETE FROM ai_support_daily_usage");
        jdbc.update("DELETE FROM ai_support_cancellation");
        jdbc.execute("CREATE TABLE IF NOT EXISTS support_flow_probe(id BIGINT PRIMARY KEY, executions INT NOT NULL)");
        jdbc.update("DELETE FROM support_flow_probe");
        jdbc.update("INSERT INTO support_flow_probe VALUES (1,0)");
        clock=mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        when(clock.withZone(any())).thenAnswer(invocation -> Clock.fixed(clock.instant(), invocation.getArgument(0)));
        reader=mock(OrderSupportReader.class);
        when(reader.lockActiveMember(anyLong())).thenReturn(true);
        when(reader.isActiveMember(1L)).thenReturn(true);
        var desk=new OrderSupportReader.Candidate(101L,"책상",NOW,"CONFIRMED");
        var context=new OrderSupportReader.Context(desk,"REQUESTED",OrderSupportReader.Eligibility.ELIGIBLE,"BEFORE_DRIVER_ACCEPTANCE",NOW,"p1");
        when(reader.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(List.of(desk),null,false, 0));
        when(reader.getContext(1L,101L)).thenReturn(context);
        store=new SupportStore(jdbc,manager,json,clock,reader);
        model=mock(SupportModel.class);
        when(model.converse(any(),any(),any(),any())).thenReturn(SupportRuntimeTest.reply("배차 수락 전이라 취소 요청이 가능해요."));
        OrderSupportCancellation gateway=(m,o)-> {
            assertThat(m).isEqualTo(1); assertThat(o).isEqualTo(101);
            jdbc.update("UPDATE support_flow_probe SET executions=executions+1 WHERE id=1");
            return new OrderSupportCancellation.Outcome(context,UUID.randomUUID().toString());
        };
        service=new SupportService(store,new SupportRuntime(model,reader,json,clock),
                new SupportConfiguration.Settings(true,null,null,4),new SupportCancellationService(store,gateway,clock,manager),new SupportCancellationResults(jdbc,reader));
        conversation=store.create(1).id();
    }
    @Test void fiftiethTextSucceedsAndFiftyFirstDoesNotCallModel() {
        jdbc.update("INSERT INTO ai_support_daily_usage VALUES(1,?,49)", LocalDate.of(2026,11,6));
        var last = find();
        assertThat(last.status()).isEqualTo(TurnStatus.SUCCEEDED);
        clearInvocations(model);
        assertThatThrownBy(() -> text("한 번 더"))
                .isInstanceOfSatisfying(SupportException.class, e -> {
                    assertThat(e.status).isEqualTo(429);
                    assertThat(e.code).isEqualTo("AI_DAILY_LIMIT");
                    assertThat(e.getMessage()).isEqualTo("오늘 상담 한도를 모두 사용했어요. 버튼 선택과 이전 대화 기록은 계속 이용할 수 있어요.");
                });
        verifyNoInteractions(model);
        assertThat(service.send(1,conversation,new SendRequest(last.clientRequestId(),last.input()))).isEqualTo(last);
        assertThat(jdbc.queryForObject("SELECT text_turns FROM ai_support_daily_usage",Integer.class)).isEqualTo(50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELECT_ORDER", "DECLINE", "NEXT_ORDERS", "CONFIRM_CANCEL"})
    void allButtonsRemainUsableAtLimitWithoutCountingOrCallingModel(String kind) {
        var source = find();
        if (kind.equals("CONFIRM_CANCEL")) source = button("SELECT_ORDER", source.turnId());
        jdbc.update("UPDATE ai_support_daily_usage SET text_turns=50 WHERE member_id=1");
        clearInvocations(model);
        var request = new SendRequest(UUID.randomUUID().toString(),
                new Input(InputKind.valueOf(kind),null,source.turnId(),kind.equals("NEXT_ORDERS") ? null : 101L));
        assertThat(service.send(1,conversation,request).status()).isEqualTo(TurnStatus.SUCCEEDED);
        verifyNoInteractions(model);
        assertThat(jdbc.queryForObject("SELECT text_turns FROM ai_support_daily_usage",Integer.class)).isEqualTo(50);
        if (kind.equals("CONFIRM_CANCEL")) {
            assertThat(executions()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT consent_rule_version FROM ai_support_cancellation",String.class)).isNull();
        }
    }

    @Test void modelFailureStillConsumesAcceptedTextTurn() {
        when(model.plan(any(),any(),any(),any())).thenThrow(new IllegalStateException("synthetic"));
        assertThat(text("주문 문의").status()).isEqualTo(TurnStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT text_turns FROM ai_support_daily_usage",Integer.class)).isEqualTo(1);
    }

    void plan(String action,String answer,String title) {
        var fields=new LinkedHashMap<String,Object>();
        fields.put("unsupportedTopic","NONE"); fields.put("action",action); fields.put("answer",answer);
        fields.put("orderId",null); fields.put("title",title); fields.put("fromDate",null);fields.put("toDate",null);
        fields.put("beforeId",null);fields.put("datePreset",null);fields.put("pageSize",1);fields.put("purpose","CANCEL_CHECK");
        when(model.plan(any(),any(),any(),any())).thenReturn(SupportRuntimeTest.reply(json.writeValueAsString(fields)));
    }
    Turn text(String value) { return service.send(1,conversation,new SendRequest(UUID.randomUUID().toString(),new Input(SupportDtos.InputKind.TEXT,value,null,null))); }
    Turn button(String type,long source) { return service.send(1,conversation,new SendRequest(UUID.randomUUID().toString(),new Input(SupportDtos.InputKind.valueOf(type),null,source,101L))); }
    int executions() { return jdbc.queryForObject("SELECT executions FROM support_flow_probe WHERE id=1",Integer.class); }
    Turn find() { plan("LATEST_ORDER","","책상"); return text("책상 주문 취소하고 싶어요"); }

    @ParameterizedTest
    @ValueSource(strings = {"orders", "order-confirmation", "eligibility", "cancel-confirmation", "receipt", "decline", "out-of-scope"})
    void responseJsonContract(String flow) throws Exception {
        Turn result;
        switch (flow) {
            case "orders" -> { plan("SEARCH_ORDER", "", null); result = text("주문 목록"); }
            case "order-confirmation" -> result = find();
            case "eligibility" -> result = button("SELECT_ORDER", find().turnId());
            case "cancel-confirmation" -> {
                button("SELECT_ORDER", find().turnId());
                plan("AFFIRM", "", null);
                result = text("네?");
            }
            case "receipt" -> result = button("CONFIRM_CANCEL", button("SELECT_ORDER", find().turnId()).turnId());
            case "decline" -> { find(); plan("DECLINE", "", null); result = text("아니요"); }
            case "out-of-scope" -> {
                when(model.plan(any(), any(), any(), any())).thenReturn(SupportRuntimeTest.planned("OUT_OF_SCOPE", "NEWS", "", null));
                result = text("뉴스 알려줘");
            }
            default -> throw new IllegalArgumentException(flow);
        }
        // Generated request identities vary; every other response field and omission is frozen.
        var normalized = (tools.jackson.databind.node.ObjectNode) json.valueToTree(result);
        normalized.put("turnId", 1);
        normalized.put("clientRequestId", "contract-request");
        var input = (tools.jackson.databind.node.ObjectNode) normalized.path("input");
        if (!input.path("sourceTurnId").isNull()) input.put("sourceTurnId", 1);
        for (var card : normalized.path("cards")) {
            if (card.has("requestId")) ((tools.jackson.databind.node.ObjectNode) card).put("requestId", "contract-receipt");
        }
        var path = java.nio.file.Path.of("src/test/resources/aisupport/contracts/" + flow + ".json");
        assertThat(json.readTree(json.writeValueAsString(normalized))).isEqualTo(json.readTree(java.nio.file.Files.readString(path)));
    }

    @ParameterizedTest
    @ValueSource(strings={"AFFIRM","DECLINE","ANSWER","CLARIFY","ORDERING_INFO"})
    void planAuditSurvivesServiceBranches(String action) {
        var candidate=find();
        var offer=button("SELECT_ORDER",candidate.turnId());
        plan(action,Set.of("ANSWER","CLARIFY").contains(action) ? "현재 질문을 확인할게요" : "",null);
        var result=text(action.equals("AFFIRM") ? "네?" : "테스트 질문");
        assertThat(result.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        var plans=json.readTree(audit(result)).valueStream()
                .filter(entry -> "plan".equals(entry.path("name").asString())).toList();
        assertThat(plans).hasSize(1);
        assertThat(plans.getFirst().path("action").asString()).isEqualTo(action);
        assertThat(plans.getFirst().path("unsupportedTopic").asString()).isEqualTo("NONE");
        assertThat(audit(offer)).doesNotContain("\"name\":\"plan\"");
    }

    @ParameterizedTest
    @CsvSource({"TEXT,TEXT","TEXT,BUTTON","BUTTON,TEXT","BUTTON,BUTTON"})
    void twoDistinctConfirmationsAreRequiredAndFinalConsentExecutesExactlyOnce(String orderMode,String consentMode) {
        var candidate=find();
        assertThat(candidate.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(candidate.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("confirmLatest",true);
        assertThat(executions()).isZero();
        verify(reader,never()).getContext(any(),any());
        plan("AFFIRM","",null);
        var offer=orderMode.equals("TEXT")?text("네"):button("SELECT_ORDER",candidate.turnId());
        assertThat(offer.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(offer.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("cancellationOffer",true);
        assertThat(offer.answer()).contains("취소 여부를 선택해 주세요");
        assertThat(executions()).isZero();
        plan("AFFIRM","",null);
        var request=new SendRequest("final",consentMode.equals("TEXT")?new Input(SupportDtos.InputKind.TEXT,"응 취소해줘",null,null):new Input(SupportDtos.InputKind.CONFIRM_CANCEL,null,offer.turnId(),101L));
        var receipt=service.send(1,conversation,request);
        assertThat(receipt.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(receipt.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("type","CANCELLATION_REQUEST");
        assertThat(receipt.answer()).contains("접수").doesNotContain("취소가 완료");
        assertThat(executions()).isEqualTo(1);
        assertThat(service.send(1,conversation,request)).isEqualTo(receipt);
        assertThat(executions()).isEqualTo(1);
    }
    @ParameterizedTest
    @ValueSource(ints = {0, 60, 61})
    void followupUsesCompactConfirmationAndOnlyRefreshesFactsOlderThanOneMinute(int age) {
        button("SELECT_ORDER", find().turnId());
        when(clock.instant()).thenReturn(NOW.plusSeconds(age));
        var order = new OrderSupportReader.Candidate(101L, "책상", NOW, "CONFIRMED");
        when(reader.getContext(1L, 101L)).thenReturn(new OrderSupportReader.Context(order, "REQUESTED",
                OrderSupportReader.Eligibility.ELIGIBLE, "BEFORE_DRIVER_ACCEPTANCE", NOW.plusSeconds(age), "p1"));
        plan("ANSWER", "수수료 여부는 확인할 수 없어요.", null);
        clearInvocations(reader, model);
        var result = text("네 근데 수수료 있어요?");
        assertThat(result.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(result.answer()).isEqualTo("수수료 여부는 확인할 수 없어요.\n\n이 주문을 취소할까요?");
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).hasSize(1);
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("confirmationOnly", true);
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("observedAt").toString())
                .isEqualTo((age > 60 ? clock.instant() : NOW).toString());
        verify(reader, times(age > 60 ? 1 : 0)).getContext(1L, 101L);
        verify(model, times(1)).plan(any(), any(), any(), any());
        verify(model, never()).converse(any(), any(), any(), any());
        assertThat(executions()).isZero();
    }

    @Test
    void testServiceNoticeAppearsAtReceiptNotEveryConfirmation() {
        var offer = button("SELECT_ORDER", find().turnId());
        assertThat(offer.answer()).doesNotContain("테스트 서비스");
        var receipt = button("CONFIRM_CANCEL", offer.turnId());
        assertThat(receipt.answer().split("테스트 서비스", -1)).hasSize(2);
    }

    @Test void plainYesWithoutActiveQuestionNeverExecutes() {
        plan("AFFIRM","",null);
        var result=text("네");
        assertThat(executions()).isZero();
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).noneMatch(c->"CANCELLATION_REQUEST".equals(c.get("type")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "네?", "네 근데 환불은 언제야", "무료면 취소해", "취소하지 마", "아니 네",
            "네네 잠깐만요", "음 네 아마도", "네 수수료 있어요?", "취소할지 고민 중이에요"
    })
    void modelAffirmCannotExecuteAmbiguousFinalConsent(String input) {
        var offer = button("SELECT_ORDER", find().turnId());
        plan("AFFIRM", "", null); // Deliberately wrong model decision: the server must still block it.

        var refreshed = text(input);

        assertThat(executions()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_cancellation", Integer.class)).isZero();
        assertThat(refreshed.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(refreshed.answer()).contains("안전을 위해", "취소 요청", "버튼").doesNotContain("명확한 최종 동의인지");
        assertThat(refreshed.cards().stream().map(SupportCardCodec::fields).toList()).anyMatch(c -> Boolean.TRUE.equals(c.get("cancellationOffer")));
        assertThat(store.question(conversation).kind()).isEqualTo(SupportDtos.QuestionKind.CANCEL);
        assertThat(store.question(conversation).orderId()).isEqualTo(101L);
        assertThat(store.question(conversation).sourceTurnId()).isEqualTo(refreshed.turnId());
        assertRuleAudit(refreshed, "NOT_EXPLICIT");
        assertThatThrownBy(() -> button("CONFIRM_CANCEL", offer.turnId())).isInstanceOf(SupportException.class);

        var receipt = button("CONFIRM_CANCEL", refreshed.turnId());
        assertThat(receipt.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(executions()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT consent_method FROM ai_support_cancellation", String.class))
                .isEqualTo("BUTTON");
        assertThat(jdbc.queryForObject("SELECT consent_rule_version FROM ai_support_cancellation", String.class)).isNull();
        assertThat(audit(receipt)).doesNotContain("final_consent_rule");
    }

    @ParameterizedTest
    @ValueSource(strings = {"네", "응", "맞아요", "네 취소해주세요", "응 취소해줘", "네, 진행해 주세요", "네 취소 해주세요", "넹", "취소해 주세요", "네~"})
    void explicitTextConsentExecutesAndRecordsRuleWithoutCopyingTextToBusinessHistory(String input) {
        button("SELECT_ORDER", find().turnId());
        plan("AFFIRM", "", null);

        var receipt = text(input);

        assertThat(receipt.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(executions()).isEqualTo(1);
        assertRuleAudit(receipt, "EXPLICIT");
        var history = jdbc.queryForMap("SELECT * FROM ai_support_cancellation");
        assertThat(history).containsEntry("consent_method", "TEXT");
        assertThat(history).containsEntry("consent_rule_version", FinalConsentRule.VERSION);
        assertThat(history).doesNotContainKeys("text", "input_text", "answer");
        assertThat(history.values()).doesNotContain(input);
    }

    @Test
    void explicitWordsWithoutModelAffirmStillRequireReconfirmation() {
        button("SELECT_ORDER", find().turnId());
        plan("CLARIFY", "취소 여부를 다시 확인해 주세요.", null);

        var refreshed = text("네 취소해주세요");

        assertThat(executions()).isZero();
        assertThat(refreshed.cards().stream().map(SupportCardCodec::fields).toList()).anyMatch(c -> Boolean.TRUE.equals(c.get("cancellationOffer")));
        assertThat(audit(refreshed)).doesNotContain("final_consent_rule");
    }

    @Test
    void finalConsentRuleDoesNotChangeOrderConfirmationAffirm() {
        find();
        plan("AFFIRM", "", null);

        var offer = text("네 그 책상이 맞는 것 같아요");

        assertThat(offer.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(store.question(conversation).kind()).isEqualTo(SupportDtos.QuestionKind.CANCEL);
        assertThat(executions()).isZero();
        assertThat(audit(offer)).doesNotContain("final_consent_rule");
    }

    @Test
    void blockedTextDoesNotOfferCancellationWhenCurrentOrderBecameIneligible() {
        button("SELECT_ORDER", find().turnId());
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        plan("AFFIRM", "", null);
        var order = new OrderSupportReader.Candidate(101L, "책상", NOW, "CONFIRMED");
        when(reader.getContext(1L, 101L)).thenReturn(new OrderSupportReader.Context(order, "ACCEPTED",
                OrderSupportReader.Eligibility.INELIGIBLE, "DRIVER_ACCEPTED", NOW, "p1"));
        when(model.converse(any(), any(), any(), any()))
                .thenReturn(SupportRuntimeTest.reply("배차 수락 후에는 취소를 요청할 수 없어요."));

        var result = text("네?");

        assertThat(result.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(executions()).isZero();
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).noneMatch(c -> Boolean.TRUE.equals(c.get("cancellationOffer")));
        assertThat(result.answer()).doesNotContain("버튼을 눌러");
        assertThat(store.question(conversation)).isNull();
        assertRuleAudit(result, "NOT_EXPLICIT");
    }

    @Test
    void failedReconfirmationStillAuditsRuleAndNeverExecutes() {
        button("SELECT_ORDER", find().turnId());
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        plan("AFFIRM", "", null);
        when(reader.getContext(1L, 101L))
                .thenThrow(new SupportException(503, "LOOKUP_FAILED", "합성 조회 오류"));

        var result = text("네?");

        assertThat(result.status()).isEqualTo(SupportDtos.TurnStatus.FAILED);
        assertThat(executions()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_cancellation", Integer.class)).isZero();
        assertThat(store.question(conversation)).isNull();
        assertRuleAudit(result, "NOT_EXPLICIT");
    }

    String audit(Turn turn) {
        return jdbc.queryForObject("SELECT tool_summary_json FROM ai_support_turn WHERE id=?", String.class, turn.turnId());
    }

    void assertRuleAudit(Turn turn, String outcome) {
        var entries = json.readTree(audit(turn));
        var rules = new ArrayList<tools.jackson.databind.JsonNode>();
        entries.forEach(entry -> {
            if ("final_consent_rule".equals(entry.path("name").asText())) rules.add(entry);
        });
        assertThat(rules).hasSize(1);
        assertThat(rules.getFirst().path("outcome").asText()).isEqualTo(outcome);
        assertThat(rules.getFirst().path("version").asText()).isEqualTo(FinalConsentRule.VERSION);
        assertThat(rules.getFirst().size()).isEqualTo(3); // Name, outcome, version only; no duplicated raw text.
    }
    @Test void questionIsNotConsentAndOldButtonIsInvalidAfterReconfirmation() {
        var candidate=find(); var offer=button("SELECT_ORDER",candidate.turnId());
        plan("CLARIFY","실제 결제가 없는 테스트 서비스입니다.",null);
        var refreshed=text("응 그런데 환불은 언제야?");
        assertThat(refreshed.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(refreshed.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("cancellationOffer",true);
        assertThat(executions()).isZero();
        assertThatThrownBy(()->button("CONFIRM_CANCEL",offer.turnId())).isInstanceOf(SupportException.class);
        plan("AFFIRM","",null); text("응 취소해줘");
        assertThat(executions()).isEqualTo(1);
    }
    @Test void oldOrderConfirmationCannotReplaceFinalQuestion() {
        var candidate=find(); var offer=button("SELECT_ORDER",candidate.turnId());
        assertThatThrownBy(()->button("SELECT_ORDER",candidate.turnId())).isInstanceOf(SupportException.class);
        assertThat(store.question(conversation).sourceTurnId()).isEqualTo(offer.turnId());
        assertThat(executions()).isZero();
    }
    @Test void acceptedBusinessRecordSurvivesConversationDeletionWithoutCopyingMessages() {
        var candidate=find(); var offer=button("SELECT_ORDER",candidate.turnId());
        var receipt=button("CONFIRM_CANCEL",offer.turnId());
        String requestId=(String)receipt.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("requestId");
        store.delete(1,conversation);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_support_cancellation WHERE request_id=?",String.class,requestId)).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_turn WHERE conversation_id=?",Integer.class,conversation)).isZero();
    }

    @Test void requestResultIsOwnerBoundIndependentAndTerminal() {
        var candidate=find();var offer=button("SELECT_ORDER",candidate.turnId());var receipt=button("CONFIRM_CANCEL",offer.turnId());
        String request=(String)receipt.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("requestId");
        var results=new SupportCancellationResults(jdbc,reader);
        assertThat(results.get(1,request).status()).isEqualTo("ACCEPTED");
        assertThatThrownBy(()->results.get(2,request)).isInstanceOf(SupportException.class);
        results.rejected(new setty.common.OrderCancellationDeclined(102L,request,NOW,"DRIVER_ACCEPTED"));
        assertThat(results.get(1,request).status()).isEqualTo("ACCEPTED");
        results.completed(new setty.common.OrderCancelled(101L,1L,request,NOW));
        results.rejected(new setty.common.OrderCancellationDeclined(101L,request,NOW.plusSeconds(1),"DRIVER_ACCEPTED"));
        store.delete(1,conversation);
        assertThat(results.get(1,request).status()).isEqualTo("COMPLETED");
        assertThat(results.get(1,request).reasonCode()).isEqualTo("CANCELLED");
        when(reader.lockActiveMember(1L)).thenReturn(false);
        assertThatThrownBy(()->results.get(1,request)).isInstanceOf(SupportException.class);
    }
    @Test void finalRejectionUsesRecordedReasonWithoutInferringCurrentOrderState() {
        var candidate=find();var offer=button("SELECT_ORDER",candidate.turnId());var receipt=button("CONFIRM_CANCEL",offer.turnId());
        String request=(String)receipt.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("requestId");
        var results=new SupportCancellationResults(jdbc,reader);
        results.rejected(new setty.common.OrderCancellationDeclined(101L,request,NOW,"ALREADY_PICKED_UP"));
        assertThat(results.get(1,request).status()).isEqualTo("REJECTED");
        assertThat(results.get(1,request).reasonCode()).isEqualTo("ALREADY_PICKED_UP");
        verify(reader,never()).getContext(2L,101L);
    }

    @Test void rejectingFirstCandidateKeepsProductFilterAndDoesNotExecute() {
        find(); plan("DECLINE","",null);
        var alternatives=text("아니요 다른 책상이에요");
        assertThat(alternatives.cards().stream().map(SupportCardCodec::fields).toList()).hasSize(1);
        assertThat(alternatives.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("type")).isEqualTo("ORDERS");
        var search=org.mockito.ArgumentCaptor.forClass(OrderSupportReader.Search.class);
        verify(reader,times(2)).findMyOrders(eq(1L),search.capture());
        assertThat(search.getValue().title()).isEqualTo("책상");
        assertThat(search.getValue().cancellationTargets()).isTrue();
        assertThat(search.getValue().excludedOrderId()).isEqualTo(101L);
        assertThat(executions()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"101","102"})
    void returningToEarlierOrderMustConfirmTargetEvenIfModelSelectsWrongCandidate(long interpretedOrder) {
        var first=find();
        var other=new OrderSupportReader.Candidate(102L,"책상",NOW.minusSeconds(60),"CONFIRMED");
        when(reader.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(List.of(other),null,false, 0));
        button("DECLINE",first.turnId());
        plan("CLARIFY","어느 주문인지 확인해 주세요.",null);
        text("아까께 맞는 것 같아요");
        when(model.plan(any(),any(),any(),any())).thenReturn(SupportRuntimeTest.planned("GET_ORDER","NONE","",interpretedOrder));
        var chosen=new OrderSupportReader.Candidate(interpretedOrder,"책상",NOW,"CONFIRMED");
        when(reader.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(List.of(chosen),null,false, 0));
        when(reader.getContext(1L,interpretedOrder)).thenReturn(new OrderSupportReader.Context(chosen,"REQUESTED",OrderSupportReader.Eligibility.ELIGIBLE,"BEFORE_DRIVER_ACCEPTANCE",NOW,"p1"));

        var confirmation=text("아까 처음에 보여주신게 맞는 것 같다고요.");
        assertThat(confirmation.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(confirmation.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("type","ORDERS").containsEntry("confirmLatest",true);
        assertThat(confirmation.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("hasUndatedOrders",false).containsKey("nextCursor");
        assertThat(store.question(conversation).kind()).isEqualTo(SupportDtos.QuestionKind.ORDER);
        assertThat(store.question(conversation).orderId()).isEqualTo(interpretedOrder);
        verify(reader,never()).getContext(any(),any());
        assertThat(executions()).isZero();
        assertThatThrownBy(()->service.send(1,conversation,new SendRequest(UUID.randomUUID().toString(),
                new Input(SupportDtos.InputKind.CONFIRM_CANCEL,null,confirmation.turnId(),interpretedOrder)))).isInstanceOf(SupportException.class);

        if (interpretedOrder==101L) {
            plan("AFFIRM","",null);
            var offer=text("맞아요");
            assertThat(store.question(conversation).kind()).isEqualTo(SupportDtos.QuestionKind.CANCEL);
            assertThat(executions()).isZero();
            button("CONFIRM_CANCEL",offer.turnId());
            assertThat(executions()).isEqualTo(1);
        }
    }

    @Test void newConversationRecoversBusinessRequestAfterOriginalChatDeletion() {
        var candidate=find(); var offer=button("SELECT_ORDER",candidate.turnId());
        var receipt=button("CONFIRM_CANCEL",offer.turnId());
        String request=(String)receipt.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("requestId");
        store.delete(1,conversation); conversation=store.create(1).id();
        var result=button("SELECT_ORDER",find().turnId());
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).anySatisfy(c->{
            if (request.equals(c.get("requestId"))) assertThat(c).containsEntry("historical",true).containsKey("previousStatus");
        });
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).anyMatch(c->request.equals(c.get("requestId")) && Boolean.TRUE.equals(c.get("historical")));
        assertThat(executions()).isEqualTo(1);
    }

    @Test void expiryAndWithdrawalKeepMinimalRequestAndAllowLateTerminalEvent() {
        var receipt=button("CONFIRM_CANCEL",button("SELECT_ORDER",find().turnId()).turnId());
        String request=(String)receipt.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("requestId");
        jdbc.update("UPDATE ai_support_conversation SET expires_at='2020-01-01' WHERE id=?",conversation);
        store.purgeExpired();
        store.withdrawn(new setty.common.MemberWithdrawn(1L));
        store.withdrawn(new setty.common.MemberWithdrawn(1L));
        var results=new SupportCancellationResults(jdbc,reader);
        results.completed(new setty.common.OrderCancelled(101L,1L,request,NOW));
        assertThat(results.get(1,request).status()).isEqualTo("COMPLETED");
        assertThatThrownBy(()->results.get(2,request)).isInstanceOf(SupportException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_conversation",Integer.class)).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM ai_support_cancellation WHERE request_id=?",request).keySet())
                .doesNotContain("input_json","answer","address","phone_number","login_id","token");
    }

    @ParameterizedTest
    @CsvSource({"COMPLETED,CANCELLED","REJECTED,DRIVER_ACCEPTED"})
    void committedDomainEventReachesResultListener(String status,String reason) {
        var receipt=button("CONFIRM_CANCEL",button("SELECT_ORDER",find().turnId()).turnId());
        String request=(String)receipt.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("requestId");
        Object event=status.equals("COMPLETED") ? new setty.common.OrderCancelled(101L,1L,request,NOW)
                : new setty.common.OrderCancellationDeclined(101L,request,NOW,reason);
        new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(s->events.publishEvent(event));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(()->
                assertThat(jdbc.queryForObject("SELECT status FROM ai_support_cancellation WHERE request_id=?",String.class,request)).isEqualTo(status));
        assertThat(jdbc.queryForObject("SELECT reason_code FROM ai_support_cancellation WHERE request_id=?",String.class,request)).isEqualTo(reason);
    }

    @Test void committedWithdrawalEventDeletesChatButKeepsBusinessEvidence() {
        var receipt=button("CONFIRM_CANCEL",button("SELECT_ORDER",find().turnId()).turnId());
        String request=(String)receipt.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("requestId");
        new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(s->events.publishEvent(new setty.common.MemberWithdrawn(1L)));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(()->
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_conversation WHERE id=?",Integer.class,conversation)).isZero());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_cancellation WHERE request_id=?",Integer.class,request)).isEqualTo(1);
    }

}
