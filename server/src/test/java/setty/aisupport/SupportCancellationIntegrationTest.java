package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static setty.aisupport.SupportDtos.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import setty.platform.OrderSupportCancellation;
import setty.platform.OrderSupportReader;
import setty.support.MySqlIntegrationTestSupport;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class SupportCancellationIntegrationTest extends MySqlIntegrationTestSupport {
    static final Instant NOW=Instant.parse("2026-11-05T01:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper json;
    SupportStore store;
    SupportCancellationService service;
    AtomicInteger calls;
    long conversation, source;
    OrderSupportReader.Context context;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM ai_support_conversation");
        jdbc.update("DELETE FROM ai_support_daily_usage");
        jdbc.update("DELETE FROM ai_support_cancellation");
        jdbc.execute("CREATE TABLE IF NOT EXISTS support_cancel_probe(id BIGINT PRIMARY KEY,requests INT NOT NULL)");
        jdbc.update("DELETE FROM support_cancel_probe");
        jdbc.update("INSERT INTO support_cancel_probe VALUES(1,0)");
        var reader=mock(OrderSupportReader.class);
        when(reader.lockActiveMember(anyLong())).thenReturn(true);
        store=new SupportStore(jdbc,manager,json,Clock.fixed(NOW,ZoneOffset.UTC),reader);
        calls=new AtomicInteger();
        context=new OrderSupportReader.Context(new OrderSupportReader.Candidate(101L,"소파",NOW,"CONFIRMED"),"REQUESTED",OrderSupportReader.Eligibility.ELIGIBLE,"BEFORE_DRIVER_ACCEPTANCE",NOW,"policy");
        service=service((member,order)->{
            assertThat(member).isEqualTo(1); assertThat(order).isEqualTo(101);
            calls.incrementAndGet(); jdbc.update("UPDATE support_cancel_probe SET requests=requests+1 WHERE id=1");
            return new OrderSupportCancellation.Outcome(context,"request-one");
        });
        conversation=store.create(1).id();
        source=offer(conversation,context);
    }
    SupportCancellationService service(OrderSupportCancellation gateway) {
        return new SupportCancellationService(store,gateway,Clock.fixed(NOW,ZoneOffset.UTC),manager);
    }
    long offer(long id,OrderSupportReader.Context context) {
        var t=store.accept(1,id,new SendRequest(UUID.randomUUID().toString(),new Input(SupportDtos.InputKind.TEXT,"취소 가능해?",null,null))).turn();
        return store.complete(1,id,t.turnId(),new Result("가능",List.of(SupportRuntime.contextCard(context)),0,0,List.of(),101L),null,"fake",1).turnId();
    }
    SendRequest confirm(String key) { return new SendRequest(key,new Input(SupportDtos.InputKind.CONFIRM_CANCEL,null,source,101L)); }
    int count() { return jdbc.queryForObject("SELECT requests FROM support_cancel_probe WHERE id=1",Integer.class); }

    @Test void finalConsentCommitsReceiptAndReplaysWithSameOrNewRequestId() {
        assertThat(count()).isZero();
        Turn first=service.confirm(1,conversation,confirm("first"));
        assertThat(first.status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(first.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("type","CANCELLATION_REQUEST").containsEntry("requestId","request-one");
        assertThat(service.confirm(1,conversation,confirm("first"))).isEqualTo(first);
        assertThat(service.confirm(1,conversation,confirm("second"))).isEqualTo(first);
        assertThat(count()).isEqualTo(1); assertThat(calls).hasValue(1);
        assertThat(first.answer()).contains("접수", "환불 완료를 뜻하지");
    }
    @Test void freeTextAnswerWithoutStructuredConsentCannotExecute() {
        var runtime=mock(SupportRuntime.class);
        when(runtime.run(anyLong(),any(),anyList(),any(),any(),any())).thenReturn(new Result("취소해 드릴게요.",List.of(),0,0,List.of(),101L));
        var orchestrator=SupportServiceFixture.service(store,runtime,new SupportConfiguration.Settings(true,null,null,4),service);
        assertThat(orchestrator.send(1,conversation,new SendRequest("yes",new Input(SupportDtos.InputKind.TEXT,"응 취소해줘",null,null))).status()).isEqualTo(SupportDtos.TurnStatus.SUCCEEDED);
        assertThat(count()).isZero(); assertThat(calls).hasValue(0);
        assertThatThrownBy(()->service.confirm(1,conversation,new SendRequest("bad",new Input(SupportDtos.InputKind.TEXT,"응",null,null)))).isInstanceOf(SupportException.class);
    }
    @Test void forgedOwnerConversationOrderAndMissingOfferAreRejected() {
        assertThatThrownBy(()->service.confirm(2,conversation,confirm("other"))).isInstanceOf(SupportException.class);
        long other=store.create(1).id();
        assertThatThrownBy(()->service.confirm(1,other,confirm("cross"))).isInstanceOf(SupportException.class);
        assertThatThrownBy(()->service.confirm(1,conversation,new SendRequest("swapped",new Input(SupportDtos.InputKind.CONFIRM_CANCEL,null,source,102L)))).isInstanceOf(SupportException.class);
        jdbc.update("UPDATE ai_support_turn SET cards_json='[]' WHERE id=?",source);
        assertThatThrownBy(()->service.confirm(1,conversation,confirm("missing"))).isInstanceOf(SupportException.class);
        assertThat(count()).isZero();
    }
    @Test void expiredOfferAndSupersededOfferCannotExecute() {
        var old=new OrderSupportReader.Context(context.order(),context.deliveryStatus(),context.eligibility(),context.reasonCode(),NOW.minusSeconds(300),"policy");
        source=offer(conversation,old);
        assertThatThrownBy(()->service.confirm(1,conversation,confirm("expired"))).isInstanceOf(SupportException.class);
        source=offer(conversation,context); offer(conversation,context);
        assertThatThrownBy(()->service.confirm(1,conversation,confirm("old"))).isInstanceOf(SupportException.class);
        assertThat(count()).isZero();
    }
    @Test void changedStateReturnsRefusalWithNoMutationAndNoModel() {
        var blocked=new OrderSupportReader.Context(context.order(),"ACCEPTED",OrderSupportReader.Eligibility.INELIGIBLE,"DRIVER_ACCEPTED",NOW,"policy");
        service=service((m,o)->new OrderSupportCancellation.Outcome(blocked,null));
        var result=service.confirm(1,conversation,confirm("changed"));
        assertThat(result.cards().stream().map(SupportCardCodec::fields).toList().getFirst()).containsEntry("reasonCode","DRIVER_ACCEPTED").doesNotContainKey("cancellationOffer");
        assertThat(result.answer()).contains("접수할 수 없어요");
        assertThat(count()).isZero();
    }
    @Test void orderMutationAndReceiptRollBackTogetherOnFailure() {
        service=service((m,o)->{jdbc.update("UPDATE support_cancel_probe SET requests=1 WHERE id=1"); throw new IllegalStateException("synthetic failure");});
        assertThatThrownBy(()->service.confirm(1,conversation,confirm("crash"))).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isZero();
        assertThat(store.existing(1,conversation,confirm("crash"))).isNull();
        assertThat(store.detail(1,conversation,null).activeTurn()).isNull();
    }
    @Test void failedReceiptPersistenceAlsoRollsBackMutation() {
        service=service((m,o)->{jdbc.update("UPDATE support_cancel_probe SET requests=1 WHERE id=1");
            jdbc.update("UPDATE ai_support_turn SET processing_deadline_at='2020-01-01' WHERE status='PROCESSING'");
            return new OrderSupportCancellation.Outcome(context,"receipt-fails");});
        assertThatThrownBy(()->service.confirm(1,conversation,confirm("receipt-fails"))).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isZero(); assertThat(store.existing(1,conversation,confirm("receipt-fails"))).isNull();
    }
    @Test void concurrentDifferentRequestIdsExecuteOnce() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await();return service.confirm(1,conversation,confirm("a"));});
            var b=pool.submit(()->{start.await();return service.confirm(1,conversation,confirm("b"));});
            start.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS).turnId()).isEqualTo(b.get(10,TimeUnit.SECONDS).turnId());
        }
        assertThat(count()).isEqualTo(1); assertThat(calls).hasValue(1);
    }
    @Test void disabledModeRejectsNewCancellationWithoutChangingHistory() {
        var orchestrator=SupportServiceFixture.service(store,mock(SupportRuntime.class),new SupportConfiguration.Settings(false,null,null,4),service);
        assertThatThrownBy(()->orchestrator.send(1,conversation,confirm("disabled"))).isInstanceOfSatisfying(SupportException.class,e->assertThat(e.code).isEqualTo("AI_DISABLED"));
        assertThat(count()).isZero(); assertThat(store.detail(1,conversation,null).items()).hasSize(1);
    }
}
