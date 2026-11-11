package setty.aisupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import tools.jackson.databind.json.JsonMapper;

/** Test-only usage recorder. The production per-turn safety limits remain separate. */
final class LocalSupportModelBudget {
    private final Path evidence;
    private final Supplier<String> modelId;
    private int attempts;
    private int responses;
    private int unknownUsage;
    private long inputTokens;
    private long outputTokens;

    LocalSupportModelBudget(Path evidence, Supplier<String> modelId) {
        this.evidence=evidence;
        this.modelId=modelId;
    }

    SupportModel wrap(SupportModel delegate) {
        return new SupportModel() {
            public Response converse(List<Message> messages, String system, List<Tool> tools, Duration remaining) {
                begin();
                Response response=delegate.converse(messages,system,tools,remaining);
                complete(response); return response;
            }
            public Response plan(List<Message> messages, String system, Map<String,Object> schema, Duration remaining) {
                begin();
                Response response=delegate.plan(messages,system,schema,remaining);
                complete(response); return response;
            }
        };
    }

    private synchronized void begin() {
        attempts++;
        unknownUsage++;
        save(); // If evidence cannot be saved, fail before making a provider request.
    }

    private synchronized void complete(SupportModel.Response response) {
        if(response!=null) {
            responses++;
            if(response.usage()!=null) {
                inputTokens+=response.usage().inputTokens();
                outputTokens+=response.usage().outputTokens();
                unknownUsage--;
            }
        }
        save();
    }

    private void save() {
        Map<String,Object> record=new LinkedHashMap<>();
        record.put("maxProviderCallAttempts",null);
        record.put("providerCallAttempts",attempts);
        record.put("providerResponses",responses);
        record.put("knownInputTokens",inputTokens);
        record.put("knownOutputTokens",outputTokens);
        record.put("usageUnknown",unknownUsage>0);
        record.put("modelId",modelId.get());
        try {
            Files.createDirectories(evidence.getParent());
            Path staged=Files.createTempFile(evidence.getParent(),"usage-",".tmp");
            try {
                Files.writeString(staged,JsonMapper.builder().build().writeValueAsString(record));
                Files.move(staged,evidence,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(staged);
            }
        } catch(IOException failure) {
            throw new IllegalStateException("Cannot record the local model budget");
        }
    }
}
