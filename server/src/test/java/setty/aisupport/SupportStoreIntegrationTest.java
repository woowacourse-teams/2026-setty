package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static setty.aisupport.SupportDtos.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import setty.common.MemberWithdrawn;
import setty.platform.OrderSupportReader;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class SupportStoreIntegrationTest extends MySqlIntegrationTestSupport {
    static final Instant NOW = Instant.parse("2026-11-04T01:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper json;
    private OrderSupportReader reader;
    private SupportStore store;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM ai_support_conversation");
        jdbc.update("DELETE FROM ai_support_daily_usage");
        reader = mock(OrderSupportReader.class);
        when(reader.lockActiveMember(anyLong())).thenReturn(true);
        store = new SupportStore(jdbc,manager,json,Clock.fixed(NOW,ZoneOffset.UTC),reader);
    }
    @Test void conversationListIncludesMessagePresenceWithoutConfusingTitleOrProcessing() {
        long empty = store.create(1).id(), filled = store.create(1).id();
        store.accept(1,filled,request("presence","새 상담"));
        var page = store.list(1,null);
        var rows = page.items();
        assertThat(json.valueToTree(page).path("items").get(0).has("hasMessages")).isTrue();
        assertThat(rows).filteredOn(c -> c.id() == empty).singleElement().satisfies(c -> assertThat(c.hasMessages()).isFalse());
        assertThat(rows).filteredOn(c -> c.id() == filled).singleElement().satisfies(c -> assertThat(c.hasMessages()).isTrue());
    }
    @Test void nextPageReferenceMustBelongToThisConversationAndMember() {
        long a=store.create(1).id(), b=store.create(1).id();
        var t=store.accept(1,a,request("first","목록")).turn();
        store.complete(1,a,t.turnId(),new Result("목록",List.of(Map.of("type","ORDERS","orders",List.of(),"nextCursor",12)),0,0,List.of()),null,"fake",1);
        assertThat(store.orderPage(1,a,t.turnId()).nextCursor()).isEqualTo(12);
        assertThatThrownBy(()->store.orderPage(2,a,t.turnId())).isInstanceOf(SupportException.class);
        assertThatThrownBy(()->store.accept(1,b,new SendRequest("next",new Input(SupportDtos.InputKind.NEXT_ORDERS,null,t.turnId(),null)))).isInstanceOf(SupportException.class);
        assertThat(store.accept(1,a,new SendRequest("next",new Input(SupportDtos.InputKind.NEXT_ORDERS,null,t.turnId(),null))).created()).isTrue();
    }
    @Test void ownerFiltersAndDisabledModePreserveHistoryWithoutCallingModel() {
        Conversation c = store.create(1);
        assertThatThrownBy(()->store.detail(2,c.id(),null)).isInstanceOfSatisfying(SupportException.class,e->assertThat(e.status).isEqualTo(404));
        SupportRuntime runtime = mock(SupportRuntime.class);
        SupportService service = SupportServiceFixture.service(store,runtime,new SupportConfiguration.Settings(false,null,null,4));
        assertThatThrownBy(()->service.send(1,c.id(),request("a","취소문의"))).isInstanceOfSatisfying(SupportException.class,e->assertThat(e.code).isEqualTo("AI_DISABLED"));
        assertThat(store.detail(1,c.id(),null).items()).isEmpty();
        assertThat(store.list(1,null).items()).hasSize(1);
        verifyNoInteractions(runtime);
    }
    @Test void duplicateDoesNotCreateTurnOrExtendRetentionAndPayloadMismatchIsConflict() {
        Conversation c = store.create(1);
        var first = store.accept(1,c.id(),request("a","문의"));
        var duplicate = store.accept(1,c.id(),request("a","문의"));
        assertThat(first.created()).isTrue(); assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.turn().turnId()).isEqualTo(first.turn().turnId());
        assertThat(store.detail(1,c.id(),null).conversation().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThatThrownBy(()->store.accept(1,c.id(),request("a","다른 내용"))).isInstanceOfSatisfying(SupportException.class,e->assertThat(e.code).isEqualTo("REQUEST_ID_CONFLICT"));
        assertThatThrownBy(()->store.accept(1,c.id(),request("b","다음"))).isInstanceOfSatisfying(SupportException.class,e->assertThat(e.code).isEqualTo("TURN_IN_PROGRESS"));
    }
    @Test void concurrentDuplicateGetsOnePersistedTurn() throws Exception {
        long id = store.create(1).id();
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<SupportStore.Acceptance> task = () -> { ready.countDown(); start.await(); return store.accept(1,id,request("a","문의")); };
            var first = executor.submit(task); var second = executor.submit(task);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results = List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            assertThat(results.stream().filter(SupportStore.Acceptance::created)).hasSize(1);
            assertThat(results.getFirst().turn().turnId()).isEqualTo(results.getLast().turn().turnId());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_turn",Long.class)).isEqualTo(1);
    }
    @Test void restartedProcessingExpiresAndLateWorkerCannotOverwriteFailure() {
        long id = store.create(1).id();
        Turn turn = store.accept(1,id,request("a","문의")).turn();
        jdbc.update("UPDATE ai_support_turn SET processing_deadline_at=? WHERE id=?",utc(NOW),turn.turnId());
        assertThat(store.detail(1,id,null).activeTurn()).isNull();
        Turn recovered = store.existing(1,id,request("a","문의"));
        assertThat(recovered.status()).isEqualTo(SupportDtos.TurnStatus.FAILED); assertThat(recovered.errorCode()).isEqualTo("PROCESSING_EXPIRED");
        assertThat(store.complete(1,id,turn.turnId(),answer(),null,"test",1).status()).isEqualTo(SupportDtos.TurnStatus.FAILED);
        assertThat(store.accept(1,id,request("b","재시도")).created()).isTrue();
    }
    @Test void expiryBlocksImmediatelyThenCascadesAndDoesNotExtendOnView() {
        long id = store.create(1).id();
        store.accept(1,id,request("a","문의"));
        jdbc.update("UPDATE ai_support_conversation SET expires_at=? WHERE id=?",utc(NOW),id);
        assertThatThrownBy(()->store.detail(1,id,null)).isInstanceOf(SupportException.class);
        assertThatThrownBy(()->store.accept(1,id,request("b","문의"))).isInstanceOf(SupportException.class);
        assertThat(store.list(1,null).items()).isEmpty();
        store.purgeExpired();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_turn",Long.class)).isZero();
    }
    @Test void directDeleteAndWithdrawalCannotResurrectInflightResults() {
        long id = store.create(1).id(); Turn turn = store.accept(1,id,request("a","문의")).turn();
        store.delete(1,id);
        assertThatThrownBy(()->store.complete(1,id,turn.turnId(),answer(),null,"test",1)).isInstanceOf(SupportException.class);
        long other = store.create(1).id(); Turn pending = store.accept(1,other,request("b","문의")).turn();
        when(reader.lockActiveMember(1L)).thenReturn(false);
        assertThatThrownBy(()->store.complete(1,other,pending.turnId(),answer(),null,"test",1)).isInstanceOfSatisfying(SupportException.class,e->assertThat(e.status).isEqualTo(401));
        store.withdrawn(new MemberWithdrawn(1L));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_turn",Long.class)).isZero();
    }
    @Test void selectionIsBoundToPresentedCandidateAndConversation() {
        long id=store.create(1).id(); Turn first=store.accept(1,id,request("a","주문" )).turn();
        Result result = new Result("선택해 주세요",List.of(Map.of("type","ORDERS","orders",List.of(Map.of("orderId",12L,"title","의자")))),0,0,List.of());
        store.complete(1,id,first.turnId(),result,null,"test",1);
        assertThatThrownBy(()->store.accept(1,id,new SendRequest("b",new Input(SupportDtos.InputKind.SELECT_ORDER,null,first.turnId(),99L)))).isInstanceOfSatisfying(SupportException.class,e->assertThat(e.code).isEqualTo("INVALID_ORDER_SELECTION"));
        long another = store.create(1).id();
        assertThatThrownBy(()->store.accept(1,another,new SendRequest("c",new Input(SupportDtos.InputKind.SELECT_ORDER,null,first.turnId(),12L)))).isInstanceOf(SupportException.class);
        assertThat(store.accept(1,id,new SendRequest("b",new Input(SupportDtos.InputKind.SELECT_ORDER,null,first.turnId(),12L))).created()).isTrue();
    }
    @Test void recentActivityPaginationUsesTimestampAndIdAndRejectsMalformedCursor() {
        List<Long> ids = new ArrayList<>(); for(int i=0;i<23;i++) ids.add(store.create(1).id());
        jdbc.update("UPDATE ai_support_conversation SET last_activity_at=? WHERE id=?",utc(NOW.plusSeconds(1)),ids.getFirst());
        ConversationPage page=store.list(1,null);
        assertThat(page.items().getFirst().id()).isEqualTo(ids.getFirst());
        assertThat(page.items()).hasSize(20);
        ConversationPage older=store.list(1,page.nextCursor());
        assertThat(older.items()).hasSize(3); assertThat(older.nextCursor()).isNull();
        assertThat(older.items()).noneMatch(c->page.items().contains(c));
        assertThatThrownBy(()->store.list(1,"bogus")).isInstanceOf(SupportException.class);
    }
    @Test void successfulResolvedSelectionIsPersistedAndReplacesPriorOrder() {
        long id=store.create(1).id(); Turn turn=store.accept(1,id,request("a","두번째")).turn();
        jdbc.update("UPDATE ai_support_conversation SET selected_order_id=12 WHERE id=?",id);
        store.complete(1,id,turn.turnId(),new Result("답변",List.of(),0,0,List.of(),30L),null,"test",1);
        assertThat(store.selected(1,id)).isEqualTo(30L);
    }
    @Test void deleteDuringModelCallCannotResurrectAndDuplicateDoesNotCallModelAgain() throws Exception {
        long id=store.create(1).id();
        SupportRuntime runtime=mock(SupportRuntime.class);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(runtime.run(anyLong(),any(),anyList(),nullable(Long.class),isNull(),nullable(SupportQuestion.class))).thenAnswer(invocation->{
            entered.countDown(); if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); return answer();
        });
        SupportService service=SupportServiceFixture.service(store,runtime,new SupportConfiguration.Settings(true,null,null,1));
        try(var executor=Executors.newSingleThreadExecutor()) {
            Future<Turn> work=executor.submit(()->service.send(1,id,request("a","문의")));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(service.send(1,id,request("a","문의")).status()).isEqualTo(SupportDtos.TurnStatus.PROCESSING);
                store.delete(1,id);
            } finally { release.countDown(); }
            assertThatThrownBy(()->work.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(SupportException.class);
        }
        verify(runtime,times(1)).run(anyLong(),any(),anyList(),nullable(Long.class),isNull(),nullable(SupportQuestion.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_conversation",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_support_turn",Long.class)).isZero();
    }
    @Test void failedSelectionClearsOldContextSoNextQuestionDoesNotUseWrongOrder() {
        long id=store.create(1).id();
        jdbc.update("UPDATE ai_support_conversation SET selected_order_id=12 WHERE id=?",id);
        Turn pending=store.accept(1,id,request("a","다른 주문" )).turn();
        store.complete(1,id,pending.turnId(),null,"LOOKUP_FAILED","test",1);
        assertThat(store.selected(1,id)).isNull();
        Turn retry=store.accept(1,id,request("b","재시도")).turn();
        jdbc.update("UPDATE ai_support_conversation SET selected_order_id=12 WHERE id=?",id);
        jdbc.update("UPDATE ai_support_turn SET processing_deadline_at=? WHERE id=?",utc(NOW),retry.turnId());
        store.detail(1,id,null);
        assertThat(store.selected(1,id)).isNull();
    }
    private static SendRequest request(String id,String text) { return new SendRequest(id,new Input(SupportDtos.InputKind.TEXT,text,null,null)); }
    private static Result answer() { return new Result("답변",List.of(),0,0,List.of()); }
    private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value,ZoneOffset.UTC); }
}
