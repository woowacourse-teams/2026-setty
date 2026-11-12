package setty.aisupport;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class LocalSupportModelBudgetTest {
    @TempDir Path directory;

    @Test void concurrentCallsAreRecordedWithoutAWholeSessionLimit() throws Exception {
        AtomicInteger called=new AtomicInteger();
        var guard=new LocalSupportModelBudget(directory.resolve("usage.json"),()->"synthetic-test-model");
        SupportModel model=guard.wrap((messages,system,tools,remaining)->{
            called.incrementAndGet();
            return new SupportModel.Response(new SupportModel.Message(SupportModel.Role.ASSISTANT,
                    List.of(new SupportModel.Text("OK"))),SupportModel.Outcome.END_TURN,new SupportModel.Usage(2,1));
        });
        try(var executor=Executors.newFixedThreadPool(8)) {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<12;i++)futures.add(executor.submit(()->{
                model.converse(List.of(),"system",List.of(),Duration.ofSeconds(1));
            }));
            for(var future:futures)future.get();
        }
        assertThat(called).hasValue(12);
        var evidence=JsonMapper.builder().build().readTree(Files.readString(directory.resolve("usage.json")));
        assertThat(evidence.get("maxProviderCallAttempts").isNull()).isTrue();
        assertThat(evidence.get("providerCallAttempts").asInt()).isEqualTo(12);
        assertThat(evidence.get("knownInputTokens").asLong()).isEqualTo(24);
        assertThat(evidence.get("knownOutputTokens").asLong()).isEqualTo(12);
        assertThat(evidence.get("usageUnknown").asBoolean()).isFalse();
    }

    @Test void missingProviderResponseRemainsUnknownWithoutPreventingLaterRequests() throws Exception {
        var guard=new LocalSupportModelBudget(directory.resolve("usage.json"),()->"synthetic-test-model");
        SupportModel model=guard.wrap((messages,system,tools,remaining)->{ throw new IllegalStateException("synthetic-timeout"); });
        for(int i=0;i<5;i++)assertThatThrownBy(()->model.converse(List.of(),"system",List.of(),Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        var evidence=JsonMapper.builder().build().readTree(Files.readString(directory.resolve("usage.json")));
        assertThat(evidence.get("usageUnknown").asBoolean()).isTrue();
        assertThat(evidence.get("providerResponses").asInt()).isZero();
        assertThat(evidence.get("providerCallAttempts").asInt()).isEqualTo(5);
    }
}
