package setty.aisupport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class SupportEvalTapeTest {
    @TempDir Path root;
    @Test void productionCannotSelectReplayProvider() {
        assertThatThrownBy(()->new SupportConfiguration.Settings(true,null,null,1,"replay"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void recordedPlanAndExplanationReplayWithoutCallingProviderAndDetectContextDrift() throws Exception {
        var json=JsonMapper.builder().build();
        var model=mock(SupportModel.class);
        when(model.plan(any(),any(),any(),any())).thenReturn(SupportRuntimeTest.planned("ANSWER","NONE","안녕하세요",null));
        when(model.converse(any(),any(),any(),any())).thenReturn(SupportRuntimeTest.reply("설명"));
        var tape=new SupportEvalTape(model,root);
        var messages=List.of(new SupportModel.Message(SupportModel.Role.USER,List.of(new SupportModel.Text("질문"))));
        Files.writeString(root.resolve("control.json"),json.writeValueAsString(Map.of("key","test-1","mode","record","tape",root.toString())));
        var p=tape.plan(messages,"system",Map.of(),Duration.ofSeconds(10));
        var c=tape.converse(messages,"system",List.of(),Duration.ofSeconds(10));
        var replay=new SupportEvalTape(model,root);
        Files.writeString(root.resolve("control.json"),json.writeValueAsString(Map.of("key","test-1","mode","replay","tape",root.toString())));
        assertThat(replay.plan(messages,"system",Map.of(),Duration.ofSeconds(10))).isEqualTo(p);
        assertThat(replay.converse(messages,"system",List.of(),Duration.ofSeconds(10))).isEqualTo(c);
        verify(model,times(1)).plan(any(),any(),any(),any());verify(model,times(1)).converse(any(),any(),any(),any());
        var drift=new SupportEvalTape(model,root);
        assertThatThrownBy(()->drift.plan(messages,"changed system",Map.of(),Duration.ofSeconds(10))).hasMessageContaining("hash differs");
    }
}
