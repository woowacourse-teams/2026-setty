package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static setty.aisupport.SupportDtos.*;
import static setty.aisupport.SupportModel.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import setty.platform.OrderSupportReader;
import tools.jackson.databind.json.JsonMapper;

class SupportRuntimeTest {
    static final Instant NOW = Instant.parse("2026-11-04T15:01:00Z");
    final SupportModel model = mock(SupportModel.class);
    final OrderSupportReader orders = mock(OrderSupportReader.class);
    final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    final SupportRuntime runtime = new SupportRuntime(model,orders,json,Clock.fixed(NOW,ZoneOffset.UTC));
    static Response planned(String action,String topic,String answer,Long id) {
        return planResponse(new SupportPlan.Plan(SupportPlan.Topic.valueOf(topic),SupportPlan.Action.valueOf(action),answer,id,null,null,null,null,null));
    }
    static Response planResponse(SupportPlan.Plan plan) {
        return reply(JsonMapper.builder().build().writeValueAsString(plan));
    }
    static Response reply(String text) { return new Response(new Message(Role.ASSISTANT,List.of(new Text(text))),Outcome.END_TURN,new Usage(10,2)); }
    Turn turn(String text) { return new Turn(1,"request",new Input(SupportDtos.InputKind.TEXT,text,null,null), SupportDtos.TurnStatus.PROCESSING,NOW,NOW.plusSeconds(60),null,List.of(),null); }
    void plan(Response response) { when(model.plan(any(),anyString(),any(),any())).thenReturn(response); }
    void context(long id,String status) {
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.getContext(1L,id)).thenReturn(new OrderSupportReader.Context(new OrderSupportReader.Candidate(id,"의자",NOW,"CONFIRMED"),status,
                OrderSupportReader.Eligibility.INELIGIBLE,"ALREADY_PICKED_UP",NOW,"p1"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(OrderSupportReader.Eligibility.class)
    void eligibilityUsesFixedAnswerWithoutExplanationModel(OrderSupportReader.Eligibility eligibility) {
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.getContext(1L,12L)).thenReturn(new OrderSupportReader.Context(
                new OrderSupportReader.Candidate(12L,"의자",NOW,"CONFIRMED"), "PICKED_UP",
                eligibility,"ALREADY_PICKED_UP",NOW,"p1"));
        var result = runtime.inspect(1,turn("맞아요"),12L);
        String expected = switch (eligibility) {
            case ELIGIBLE -> "이 주문은 지금 취소 요청할 수 있어요. 아래에서 취소 여부를 선택해 주세요.";
            case INELIGIBLE -> "이 주문은 지금 취소 요청을 할 수 없어요. 이유는 아래에서 확인해 주세요.";
            case UNKNOWN -> "주문 상태를 확인하지 못했어요. 잠시 뒤 다시 확인해 주세요.";
        };
        assertThat(result.answer()).isEqualTo(expected).doesNotContain("취소가 완료되었", "환불이 완료", "환불해 드렸", "픽업");
        assertThat(result.cards()).hasSize(1);
        assertThat(result.inputTokens()).isZero();
        assertThat(result.outputTokens()).isZero();
        verifyNoInteractions(model);
    }

    @Test
    void repeatedSelectionUsesStableFixedAnswer() {
        context(12, "PICKED_UP");
        var selected = new Turn(1, "ordered", new Input(InputKind.SELECT_ORDER, null, 1L, 12L),
                TurnStatus.PROCESSING, NOW, NOW.plusSeconds(60), null, List.of(), null);
        for (int i = 0; i < 3; i++)
            assertThat(runtime.run(1, selected, List.of(), 12L).answer())
                    .isEqualTo("이 주문은 지금 취소 요청을 할 수 없어요. 이유는 아래에서 확인해 주세요.");
        verifyNoInteractions(model);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ㄴ엔넵", "먼소리야 그런말 안했는뎅", "네 취소 해주세요", "무료면 취소해"})
    void cancelInterpretationReceivesOnlyCurrentAnswerAndOrderFacts(String current) {
        var previous = new Turn(2, "old", new Input(SupportDtos.InputKind.TEXT, "이전무료조건", null, null), SupportDtos.TurnStatus.SUCCEEDED, NOW, NOW, "이전조건을유지하나요", List.of(), null);
        plan(planned("CLARIFY", "NONE", "취소 요청을 진행할까요?", null));
        runtime.run(1, turn(current), List.of(previous), 12L, null,
                new SupportQuestion(2, SupportDtos.QuestionKind.CANCEL, 12, NOW.plusSeconds(300), Map.of()));
        var input = ArgumentCaptor.forClass(List.class);
        verify(model).plan(input.capture(), anyString(), any(), any());
        assertThat(input.getValue().toString()).contains(current, "CANCEL", "12")
                .doesNotContain("이전무료조건", "이전조건을유지하나요");
    }

    @Test void cancelledExistenceQuestionAnswersFromFreshServerResult() {
        plan(planResponse(new SupportPlan.Plan(SupportPlan.Topic.NONE, SupportPlan.Action.SEARCH_ORDER,
                "", null, null, null, null, null, null, 5, "CANCELLED_CHECK")));
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.findMyOrders(eq(1L), any())).thenReturn(new OrderSupportReader.SearchResult(
                List.of(new OrderSupportReader.Candidate(930026L, "의자", NOW, "CANCELLED")), null, false, 0));
        var result = runtime.run(1, turn("이미 취소된 주문 있나??"), List.of(), null);
        assertThat(result.answer()).contains("930026번 주문이 취소된 상태");
        var search = ArgumentCaptor.forClass(OrderSupportReader.Search.class);
        verify(orders).findMyOrders(eq(1L), search.capture());
        assertThat(search.getValue().cancelledOnly()).isTrue();
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("purpose", "CANCELLED_CHECK");
        verify(model, never()).converse(any(), any(), any(), any());
    }

    @Test void serverRefusalIsNotReplayedAsAssistantContextOnNextSupportedTurn() {
        var mixed = new SupportPlan.Plan(SupportPlan.Topic.NEWS,SupportPlan.Action.ANSWER,"주문 확인을 도와드릴게요.",null,null,null,null,null,null);
        var previous = new Turn(2,"mixed",new Input(SupportDtos.InputKind.TEXT,"최근 주문과 뉴스",null,null), SupportDtos.TurnStatus.SUCCEEDED,NOW,NOW,
                SupportPlan.finish(mixed,mixed.answer()),List.of(),null);
        plan(planned("ANSWER","NONE","목록은 주문번호 큰 순이에요.",null));
        runtime.run(1,turn("그래서 가장 최근 주문이라는거야?"),List.of(previous),null);
        var inputs=ArgumentCaptor.forClass(List.class);
        verify(model).plan(inputs.capture(),anyString(),any(),any());
        assertThat(inputs.getValue().get(1).toString()).contains("주문 확인을 도와드릴게요").doesNotContain("요청하신 뉴스·날씨 같은 실시간 정보 안내");
        assertThat(inputs.getValue().getLast().toString()).contains("이번 사용자 메시지", "그래서 가장 최근 주문이라는거야?");
    }

    @Test void emptySearchNamesItsTitleAndDatesAndOffersNextAction() {
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(List.of(),null,false, 0));
        plan(planResponse(new SupportPlan.Plan(SupportPlan.Topic.NONE,SupportPlan.Action.SEARCH_ORDER,"",null,"신발","2026-11-01","2026-11-03",null,null)));
        var result=runtime.run(1,turn("11월 1일부터 3일 신발 주문"),List.of(),null);
        assertThat(result.answer()).contains("신발","2026-11-01","2026-11-03","최근 주문");
    }

    @Test void consecutiveEmptyConditionsFallBackToRecentOrdersWithoutRepeatingDeadEnd() {
        var old=new Turn(2,"empty",new Input(SupportDtos.InputKind.TEXT,"신발 취소",null,null), SupportDtos.TurnStatus.SUCCEEDED,NOW,NOW,"신발 주문이 없어요",
                List.of(Map.of("type","ORDERS","orders",List.of(),"search",Map.of("title","신발"),"purpose","CANCEL_CHECK")),null);
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(List.of(),null,false, 0),
                new OrderSupportReader.SearchResult(List.of(new OrderSupportReader.Candidate(50L,"의자",null,"CONFIRMED")),null,false, 0));
        plan(planResponse(new SupportPlan.Plan(SupportPlan.Topic.NONE,SupportPlan.Action.LATEST_ORDER,"",null,"신발",null,null,null,null,1,"CANCEL_CHECK")));
        var result=runtime.run(1,turn("엥? 내가 주문한게 얼마나 많은데"),List.of(old),null);
        assertThat(result.answer()).contains("신발","최근 주문").doesNotContain("맞나요");
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).hasSize(1);
        assertThat(json.valueToTree(result.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).path("orders").get(0).path("orderId").asLong()).isEqualTo(50);
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("purpose","BROWSE");
        verify(orders,never()).getContext(any(),any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CANCEL_CHECK", "CANCELLED_CHECK"})
    void alreadyCancelledTextTargetReturnsStatusWithoutOrderConfirmation(String purpose) {
        var cancelled=new OrderSupportReader.Candidate(26L,"의자",NOW,"CANCELLED");
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(List.of(cancelled),null,false, 0));
        when(orders.getContext(1L,26L)).thenReturn(new OrderSupportReader.Context(cancelled,"CANCELLED",OrderSupportReader.Eligibility.INELIGIBLE,"ALREADY_CANCELLED",NOW,"p1"));
        plan(planResponse(new SupportPlan.Plan(SupportPlan.Topic.NONE, SupportPlan.Action.GET_ORDER,
                "", 26L, null, null, null, null, null, 5, purpose)));
        var result=runtime.run(1,turn("6번 취소 돼"),List.of(),null);
        assertThat(result.answer()).contains("이미 취소된 주문").doesNotContain("맞나요");
        assertThat(result.cards()).singleElement().satisfies(card -> {
            assertThat(SupportCardCodec.fields(card))
                    .containsEntry("type", "ELIGIBILITY")
                    .containsEntry("eligibility", "INELIGIBLE")
                    .containsEntry("reasonCode", "ALREADY_CANCELLED")
                    .doesNotContainKey("cancellationOffer");
        });
        assertThat(result.selectedOrderId()).isEqualTo(26L);
        verify(model,never()).converse(any(),any(),any(),any());
    }

    @Test void cancelledOnlyPurposeStillRejectsAnAffirmationPlan() {
        plan(planResponse(new SupportPlan.Plan(SupportPlan.Topic.NONE, SupportPlan.Action.AFFIRM,
                "", null, null, null, null, null, null, 5, "CANCELLED_CHECK")));

        assertThatThrownBy(() -> runtime.run(1, turn("맞아요"), List.of(), null))
                .isInstanceOfSatisfying(SupportException.class,
                        error -> assertThat(error.code).isEqualTo("INVALID_SUPPORT_PLAN"));
        verifyNoInteractions(orders);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"ORDERING_INFO","UNSUPPORTED_FILTER"})
    void sortingCapabilityAnswersDoNotRepeatTheListOrQueryOrders(String action) {
        plan(planned(action,"NONE","",null));
        var result=runtime.run(1,turn(action.equals("ORDERING_INFO")?"주문번호순으로":"금액순으로"),List.of(),null);
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).isEmpty();
        assertThat(result.answer()).contains("주문번호 큰 순");
        if (action.equals("UNSUPPORTED_FILTER")) assertThat(result.answer()).contains("지원하지","주문 내역 화면");
        else assertThat(result.answer()).doesNotContain("지원하지");
        verifyNoInteractions(orders);
    }

    @Test void orderingInfoIdentifiesTopOrderInMostRecentDisplayedCard() {
        var older = new Turn(2,"older",new Input(SupportDtos.InputKind.TEXT,"목록",null,null), SupportDtos.TurnStatus.SUCCEEDED,NOW,NOW,"목록",
                List.of(Map.of("type","ORDERS","orders",List.of(Map.of("orderId",930044L)))),null);
        var latest = new Turn(3,"latest",new Input(SupportDtos.InputKind.TEXT,"의자",null,null), SupportDtos.TurnStatus.SUCCEEDED,NOW,NOW,"맞나요?",
                List.of(Map.of("type","ORDERS","confirmLatest",true,"orders",List.of(Map.of("orderId",930025L)))),null);
        plan(planned("ORDERING_INFO","NONE","",null));
        var result=runtime.run(1,turn("그럼 그게 제일 최근 거야?"),List.of(older,latest),null);
        assertThat(result.answer()).startsWith("네,").contains("#930025", "그중 가장 최근 주문")
                .doesNotContain("930044", "주문일", "날짜 검색");
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).isEmpty();
        verifyNoInteractions(orders);
    }

    @Test void orderingInfoWithoutDisplayedOrdersGivesOnlyUserFacingRule() {
        plan(planned("ORDERING_INFO","NONE","",null));
        assertThat(runtime.run(1,turn("최근순이야?"),List.of(),null).answer())
                .isEqualTo("주문 목록은 최근 주문(주문번호 큰 순)부터 보여드려요.");
    }

    @Test void outOfScopeUsesServerRefusalWithoutReadingPreviouslySelectedOrder() {
        plan(planned("OUT_OF_SCOPE","GENERAL_KNOWLEDGE","",null));
        var result=runtime.run(1,turn("미국 수도?"),List.of(),12L);
        assertThat(result.answer()).contains("지원하지 않습니다").doesNotContain("워싱턴");
        assertThat(result.selectedOrderId()).isEqualTo(12L);verifyNoInteractions(orders);
        verify(model,never()).converse(any(),any(),any(),any());
    }
    @Test void mixedPolicyKeepsSupportedAnswerAndAddsRefusal() {
        plan(planned("ANSWER","OTHER_SERVICE","픽업 후에는 취소할 수 없어요.",null));
        var result=runtime.run(1,turn("SKT 요금제와 픽업 후 정책"),List.of(),12L);
        assertThat(result.answer()).contains("지원하지 않습니다","픽업 후에는 취소할 수 없어요.");verifyNoInteractions(orders);
    }
    @Test void greetingAndPolicyDoNotQueryAndRetainHistory() {
        var previous=new Turn(2,"old",new Input(SupportDtos.InputKind.TEXT,"파란 의자",null,null), SupportDtos.TurnStatus.SUCCEEDED,NOW,NOW,"알겠습니다",List.of(),null);
        plan(planned("ANSWER","NONE","안녕하세요!",null));
        assertThat(runtime.run(1,turn("안녕"),List.of(previous),12L).answer()).isEqualTo("안녕하세요!");
        var input=ArgumentCaptor.forClass(List.class);var prompt=ArgumentCaptor.forClass(String.class);
        verify(model).plan(input.capture(),prompt.capture(),any(),any());
        assertThat(input.getValue().toString()).contains("파란 의자","알겠습니다","\"selectedOrderId\":12");
        assertThat(prompt.getValue()).contains("cancellation-knowledge-v2","현재 한국 날짜: 2026-11-05");
        verifyNoInteractions(orders);
    }
    @Test void confirmedOrderGetsOneAuthorizedLookupAndToolFreeExplanation() {
        context(12L,"PICKED_UP");
        when(model.converse(any(),any(),any(),any())).thenReturn(reply("픽업 후라 취소할 수 없어요."));
        var r=runtime.inspect(1,turn("맞아요"),12L);
        assertThat(r.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("eligibility")).isEqualTo("INELIGIBLE");
        assertThat(r.selectedOrderId()).isEqualTo(12L);assertThat(r.inputTokens()).isZero();
        verify(orders).getContext(1L,12L);verifyNoInteractions(model);
    }
    @Test void explicitSelectionSkipsPlannerButChecksMembership() {
        context(30L,"PICKED_UP");when(model.converse(any(),any(),any(),any())).thenReturn(reply("픽업 후입니다."));
        var t=new Turn(1,"select",new Input(SupportDtos.InputKind.SELECT_ORDER,null,2L,30L), SupportDtos.TurnStatus.PROCESSING,NOW,NOW.plusSeconds(60),null,List.of(),null);
        assertThat(runtime.run(1,t,List.of(),12L).selectedOrderId()).isEqualTo(30L);
        verify(model,never()).plan(any(),any(),any(),any());verify(orders).isActiveMember(1L);verify(orders).getContext(1L,30L);
    }
    @Test void searchClearsSelectionFormatsKstAndUsesOneModelCall() {
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(List.of(
                new OrderSupportReader.Candidate(12L,"의자",Instant.parse("2026-11-03T15:00:00Z"),"CONFIRMED"),
                new OrderSupportReader.Candidate(11L,"책상",Instant.parse("2026-11-03T14:59:59.999999Z"),"CONFIRMED")),11L,true, 1));
        plan(planned("SEARCH_ORDER","NONE","",null));
        var r=runtime.run(1,turn("다른 주문"),List.of(),99L);
        assertThat(r.answer()).contains("주문 2건").doesNotContain("의자","2026-11-04");assertThat(r.selectedOrderId()).isNull();
        assertThat(json.valueToTree(r.cards().stream().map(SupportCardCodec::fields).toList()).get(0).path("orders").get(0).path("createdDateKst").asString()).isEqualTo("2026-11-04");
        verify(model,never()).converse(any(),any(),any(),any());
    }
    @Test void relativeDateAndPaginationFiltersSurviveRouting() {
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(List.of(),null,true, 1));
        plan(planResponse(new SupportPlan.Plan(SupportPlan.Topic.NONE,SupportPlan.Action.SEARCH_ORDER,"",null,"소파",null,null,12L,"YESTERDAY")));
        var r=runtime.run(1,turn("어제 주문 다음 페이지"),List.of(),null);
        verify(orders).findMyOrders(1L,OrderSupportReader.Search.browse(null, "소파", LocalDate.parse("2026-11-04"), LocalDate.parse("2026-11-04"), 12L, 5));
        assertThat(r.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("hasUndatedOrders")).isEqualTo(true);
        assertThat(json.valueToTree(r.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("search")).path("fromDate").asString()).isEqualTo("2026-11-04");
        assertThat(json.valueToTree(r.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("search")).path("datePreset").isNull()).isTrue();
    }
    @Test void lastCandidateGroupSuppliesIdentityAndCursorWithoutStatusOrDates() {
        var old=new Turn(2,"old",new Input(SupportDtos.InputKind.TEXT,"주문",null,null), SupportDtos.TurnStatus.SUCCEEDED,NOW,NOW,"선택해주세요",
                List.of(Map.of("type","ORDERS","orders",List.of(Map.of("orderId",30L,"title","책상","createdAt","2026-11-01T01:00:00Z","orderStatus","CONFIRMED")),"nextCursor",30L)),null);
        plan(planned("CLARIFY","NONE","이 책상 주문인가요?",null));
        runtime.run(1,turn("첫 번째 말고 다른 주문"),List.of(old),12L);
        var inputs=ArgumentCaptor.forClass(List.class);verify(model).plan(inputs.capture(),anyString(),any(),any());
        var current=(Message)inputs.getValue().getLast();
        assertThat(current.toString()).contains("책상","\"nextCursor\":30").doesNotContain("createdAt","CONFIRMED","2026-11-01");
        verifyNoInteractions(orders);
    }
    @Test void realtimeInformationRefusalNamesNewsAndWeather() {
        plan(planned("OUT_OF_SCOPE","NEWS","",null));
        var result = runtime.run(1,turn("날씨 알려줘"),List.of(),null);
        assertThat(result.answer()).startsWith("요청하신 뉴스·날씨 같은 실시간 정보 안내는 이 상담에서 지원하지 않습니다.");
        assertThat(SupportPlan.withoutServerRefusal(result.answer())).isEmpty();
        assertThat(SupportPlan.Topic.NEWS.name()).isEqualTo("NEWS");
    }
    @Test void unsupportedActionNamesCancellationWithdrawalAndLegacyNewsIsRemoved() {
        plan(planned("OUT_OF_SCOPE","UNSUPPORTED_ACTION","",null));
        var result = runtime.run(1,turn("취소 철회해줘"),List.of(),null);
        assertThat(result.answer()).startsWith("요청하신 환불 실행·취소 철회·배송원 연락은 이 상담에서 지원하지 않습니다.");
        String old = "요청하신 뉴스 안내는 이 상담에서 지원하지 않습니다. 본인 주문 찾기와 현재 취소 가능 여부, 취소 정책을 안내해 드릴 수 있어요.";
        assertThat(SupportPlan.withoutServerRefusal(old)).isEmpty();
        assertThat(SupportPlan.withoutServerRefusal(old + "\n\n주문 안내")).isEqualTo("주문 안내");
    }
    @Test void unspecifiedOutOfScopeReturnsRemovableGenericRefusal() {
        plan(planned("OUT_OF_SCOPE","NONE","",null));
        var result = runtime.run(1,turn("문의"),List.of(),null);
        assertThat(result.answer()).isEqualTo("요청하신 기능은 이 상담에서 지원하지 않습니다. 본인 주문 찾기와 현재 취소 가능 여부, 취소 정책을 안내해 드릴 수 있어요.");
        assertThat(SupportPlan.withoutServerRefusal(result.answer())).isEmpty();
        assertThat(SupportPlan.withoutServerRefusal(result.answer()+"\n\n주문 안내")).isEqualTo("주문 안내");
        verifyNoInteractions(orders);
    }
    @Test void invalidPlanFailsBeforeDomainAndKeepsUsage() {
        plan(planned("OUT_OF_SCOPE","NONE","허용하지 않는 답변",null));
        assertThatThrownBy(()->runtime.run(1,turn("문의"),List.of(),null)).isInstanceOfSatisfying(SupportException.class,e->{
            assertThat(e.code).isEqualTo("INVALID_SUPPORT_PLAN");assertThat(e.evidence.inputTokens()).isEqualTo(10);
        });verifyNoInteractions(orders);
    }
    @Test void numbersCannotBeStringsFractionalOrNegative() {
        for(String n:List.of("\"12\"","12.5","-1","9223372036854775808")) {
            String text=((Text)planned("GET_ORDER","NONE","",12L).message().content().getFirst()).text().replace("\"orderId\":12","\"orderId\":"+n);
            plan(reply(text));assertThatThrownBy(()->runtime.run(1,turn("조회"),List.of(),null)).isInstanceOf(SupportException.class);
        }verifyNoInteractions(orders);
    }
    @Test void inactiveMemberNeverReachesLookup() {
        plan(planned("GET_ORDER","NONE","",12L));
        assertThatThrownBy(()->runtime.run(1,turn("조회"),List.of(),null)).isInstanceOfSatisfying(SupportException.class,e->assertThat(e.code).isEqualTo("INVALID_TOKEN"));
        verify(orders,never()).getContext(any(),any());
    }
    @Test void lookupFailureDoesNotAskModelForAnInventedAnswer() {
        when(orders.isActiveMember(1L)).thenReturn(true);when(orders.getContext(1L,12L)).thenThrow(new IllegalStateException());
        assertThatThrownBy(()->runtime.inspect(1,turn("맞아요"),12L)).isInstanceOfSatisfying(SupportException.class,e->assertThat(e.code).isEqualTo("LOOKUP_FAILED"));
        verify(model,never()).converse(any(),any(),any(),any());
    }
    @Test void fixedExplanationCannotRequestAnotherTool() {
        context(12L,"PICKED_UP");
        assertThat(runtime.inspect(1,turn("맞아요"),12L).answer()).contains("이유는 아래에서");
        verify(orders,never()).findMyOrders(any(),any());verifyNoInteractions(model);
    }
    @Test void expiredDeadlineDoesNotReadOrCallModel() {
        var t=new Turn(1,"r",new Input(SupportDtos.InputKind.TEXT,"문의",null,null), SupportDtos.TurnStatus.PROCESSING,NOW,NOW.minusSeconds(1),null,List.of(),null);
        assertThatThrownBy(()->runtime.run(1,t,List.of(),null)).isInstanceOfSatisfying(SupportException.class,e->assertThat(e.code).isEqualTo("PROCESSING_EXPIRED"));
        verifyNoInteractions(model,orders);
    }
    @Test void bedrockPlanToolIsParsedButUnknownToolCannotExecute() {
        var p=new SupportPlan.Plan(SupportPlan.Topic.NONE,SupportPlan.Action.ANSWER,"안녕하세요",null,null,null,null,null,null);
        Map<String,Object> fields=json.convertValue(p,Map.class);
        plan(new Response(new Message(Role.ASSISTANT,List.of(new ToolUse("p","support_plan",fields))),Outcome.TOOL_USE,new Usage(1,1)));
        assertThat(runtime.run(1,turn("안녕"),List.of(),null).answer()).isEqualTo("안녕하세요");
        plan(new Response(new Message(Role.ASSISTANT,List.of(new ToolUse("p","cancel_order",fields))),Outcome.TOOL_USE,null));
        assertThatThrownBy(()->runtime.run(1,turn("안녕"),List.of(),null)).isInstanceOf(SupportException.class);verifyNoInteractions(orders);
    }
    @Test void nextButtonUsesStoredConditionsWithoutModelAndRetainsPurpose() {
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(List.of(),null,false, 0));
        var page=Map.<String,Object>of("type","ORDERS","orders",List.of(),"nextCursor",12L,"pageSize",10,"ordering","ORDER_ID_DESC","purpose","CANCEL_CHECK",
                "search",Map.of("title","소파","fromDate","2026-11-03","toDate","2026-11-03"));
        var next=new Turn(1,"next",new Input(SupportDtos.InputKind.NEXT_ORDERS,null,2L,null), SupportDtos.TurnStatus.PROCESSING,NOW,NOW.plusSeconds(60),null,List.of(),null);
        var r=runtime.run(1,next,List.of(),null,SupportCardCodec.read(page));
        verifyNoInteractions(model);
        verify(orders).findMyOrders(1L,OrderSupportReader.Search.cancellationTargets(null, "소파", LocalDate.of(2026,11,3), LocalDate.of(2026,11,3), 12L, 10, null));
        assertThat(r.cards().stream().map(SupportCardCodec::fields).toList().getFirst().get("purpose")).isEqualTo("CANCEL_CHECK");
    }
    @Test void legacyDateSortedCancellationCursorMustNotBeReinterpretedAsIdCursor() {
        var page=Map.<String,Object>of("type","ORDERS","orders",List.of(),"nextCursor",12L,"purpose","CANCEL_CHECK","search",Map.of("title","소파"));
        var next=new Turn(1,"next",new Input(SupportDtos.InputKind.NEXT_ORDERS,null,2L,null), SupportDtos.TurnStatus.PROCESSING,NOW,NOW.plusSeconds(60),null,List.of(),null);
        var result=runtime.run(1,next,List.of(),null,SupportCardCodec.read(page));
        assertThat(result.answer()).contains("정렬 기준","새로 검색");
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).isEmpty();
        verifyNoInteractions(model,orders);
    }

    @Test void nextAfterEndReturnsEndWithoutLookupOrButtonModel() {
        var page=new LinkedHashMap<String,Object>();page.put("type","ORDERS");page.put("orders",List.of());page.put("nextCursor",null);
        var next=new Turn(1,"next",new Input(SupportDtos.InputKind.NEXT_ORDERS,null,2L,null), SupportDtos.TurnStatus.PROCESSING,NOW,NOW.plusSeconds(60),null,List.of(),null);
        assertThat(runtime.run(1,next,List.of(),null,SupportCardCodec.read(page)).answer()).contains("모든 주문");
        verifyNoInteractions(model,orders);
        plan(planned("NEXT_ORDERS","NONE","",null));
        var past=new Turn(2,"past",new Input(SupportDtos.InputKind.TEXT,"주문",null,null), SupportDtos.TurnStatus.SUCCEEDED,NOW,NOW,"목록",List.of(page),null);
        assertThat(runtime.run(1,turn("다음"),List.of(past),null).answer()).contains("모든 주문");
        verifyNoInteractions(orders);
    }
    @Test void requestedPageSizeCannotExceedServerBound() {
        plan(planResponse(new SupportPlan.Plan(SupportPlan.Topic.NONE,SupportPlan.Action.SEARCH_ORDER,"",null,null,null,null,null,null,21,"BROWSE")));
        assertThatThrownBy(()->runtime.run(1,turn("21개"),List.of(),null)).isInstanceOf(SupportException.class);
        verifyNoInteractions(orders);
    }

    @Test
    void latestOrderFirstAsksForConfirmationWithoutEligibilityLookup() {
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(
            List.of(new OrderSupportReader.Candidate(12L,"소파",NOW,"CONFIRMED")),null,true, 1));
        plan(planned("LATEST_ORDER","NONE","",null));
        var result=runtime.run(1,turn("최근에 주문한거 취소 가능한가?"),List.of(),99L);
        verify(orders).findMyOrders(1L,OrderSupportReader.Search.latest(null));
        verify(orders,never()).getContext(anyLong(),anyLong());
        verify(model,never()).converse(any(),any(),any(),any());
        assertThat(result.answer()).contains("가장 최근", "맞나요?").doesNotContain("주문일이 확인되는");
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("confirmLatest",true);
        assertThat(result.selectedOrderId()).isNull();
    }

    @Test
    void latestUnknownDateAndEmptyAccountHaveDifferentMessages() {
        assertThat(SupportPresentation.renderLatest(new OrderSupportReader.SearchResult(List.of(),null,true, 1)))
            .contains("주문이 없어요").doesNotContain("주문일 정보가 없어");
        assertThat(SupportPresentation.renderLatest(new OrderSupportReader.SearchResult(List.of(),null,false, 0)))
            .contains("취소 완료를 제외한 조건에 맞는 주문이 없어요");
    }
    @Test
    void deskCancellationStartsWithOneMatchingOrderAndDoesNotReadEligibilityYet() {
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(
                List.of(new OrderSupportReader.Candidate(12L,"책상",NOW,"CONFIRMED")),null,false, 0));
        plan(planResponse(new SupportPlan.Plan(SupportPlan.Topic.NONE,SupportPlan.Action.LATEST_ORDER,
                "",null,"책상",null,null,null,null,1,"CANCEL_CHECK")));
        var result=runtime.run(1,turn("책상 주문 취소하고 싶어요"),List.of(),null);
        verify(orders).findMyOrders(1L,OrderSupportReader.Search.latest("책상"));
        verify(orders,never()).getContext(any(),any());
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).hasSize(1);
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("confirmLatest",true);
        assertThat(result.selectedOrderId()).isNull();
        assertThat(result.answer()).contains("맞나요");
    }

    @Test void rejectedButtonStaysARejectionInSubsequentModelHistory() {
        var rejection=new Turn(25,"no",new Input(SupportDtos.InputKind.DECLINE,null,24L,930025L), SupportDtos.TurnStatus.SUCCEEDED,NOW,NOW,
                "다른 주문이에요",List.of(),null);
        plan(planned("CLARIFY","NONE","어느 주문인가요?",null));
        runtime.run(1,turn("아까께 맞아요"),List.of(rejection),null);
        var inputs=ArgumentCaptor.forClass(List.class);
        verify(model).plan(inputs.capture(),anyString(),any(),any());
        var message=(Message)inputs.getValue().getFirst();
        assertThat(message.toString()).contains("930025","거절","24").doesNotContain("선택했습니다");
    }

    @Test void eachPresentedGroupKeepsItsTurnAndOrderIdentitiesInModelHistory() {
        var first=new Turn(24,"first",new Input(SupportDtos.InputKind.TEXT,"의자 취소",null,null), SupportDtos.TurnStatus.SUCCEEDED,NOW,NOW,"이 주문 맞나요?",
                List.of(Map.of("type","ORDERS","confirmLatest",true,"orders",List.of(Map.of("orderId",930025L,"title","의자")))),null);
        var alternatives=new Turn(25,"others",new Input(SupportDtos.InputKind.DECLINE,null,24L,930025L), SupportDtos.TurnStatus.SUCCEEDED,NOW,NOW,"다른 주문이에요",
                List.of(Map.of("type","ORDERS","orders",List.of(Map.of("orderId",930024L,"title","의자")))),null);
        plan(planned("CLARIFY","NONE","다시 확인할게요",null));
        runtime.run(1,turn("처음 보여준 주문"),List.of(first,alternatives),null);
        var inputs=ArgumentCaptor.forClass(List.class);
        verify(model).plan(inputs.capture(),anyString(),any(),any());
        assertThat(inputs.getValue().get(1).toString()).contains("\"sourceTurnId\":24","930025").doesNotContain("930024");
        assertThat(inputs.getValue().get(3).toString()).contains("\"sourceTurnId\":25","930024");
    }

    @Test void unavailableReferencedOrderNeverFallsBackOrOffersCancellation() {
        when(orders.isActiveMember(1L)).thenReturn(true);
        when(orders.findMyOrders(eq(1L),any())).thenReturn(new OrderSupportReader.SearchResult(List.of(),null,false, 0));
        plan(planned("GET_ORDER","NONE","",930025L));
        var result=runtime.run(1,turn("처음 주문"),List.of(),930024L);
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList()).isEmpty();
        assertThat(result.selectedOrderId()).isNull();
        assertThat(result.answer()).contains("찾지 못했어요");
        verify(orders).findMyOrders(1L,OrderSupportReader.Search.browse(930025L, null, null, null, null, 1));
        verify(orders,never()).getContext(any(),any());
        verify(model,never()).converse(any(),any(),any(),any());
    }

}
