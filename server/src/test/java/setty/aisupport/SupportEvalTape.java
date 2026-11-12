package setty.aisupport;

import static setty.aisupport.SupportModel.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in evaluation instrumentation: test source only, never a production provider. */
final class SupportEvalTape implements SupportModel {
    private final SupportModel delegate;
    private final Path root;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private String lastKey = "";
    private int sequence;
    private double spent;
    private int errors;

    SupportEvalTape(SupportModel delegate, Path root) { this.delegate=delegate; this.root=root.toAbsolutePath().normalize(); }
    @Override public Response plan(List<Message> messages,String system,Map<String,Object> schema,Duration remaining) {
        return call("plan",messages,system,schema,()->delegate.plan(messages,system,schema,remaining));
    }
    @Override public Response converse(List<Message> messages,String system,List<Tool> tools,Duration remaining) {
        return call("converse",messages,system,tools,()->delegate.converse(messages,system,tools,remaining));
    }
    private synchronized Response call(String kind,List<Message> messages,String system,Object contract,Supplier<Response> invoke) {
        try {
            var control=json.readTree(Files.readString(root.resolve("control.json")));
            String key=control.path("key").asString();
            String mode=control.path("mode").asString();
            if (!Set.of("record","replay").contains(mode) || !key.matches("[A-Za-z0-9_-]+")) throw new IllegalStateException("Invalid evaluation control");
            if (!key.equals(lastKey)) { lastKey=key; sequence=0; }
            int index=++sequence;
            Path directory=Path.of(control.path("tape").asString()).toAbsolutePath().normalize();
            if(!directory.startsWith(root)) throw new IllegalStateException("Tape outside evaluation root");
            Files.createDirectories(directory);
            String request=json.writeValueAsString(canonical(json.convertValue(Map.of("kind",kind,"messages",messages,"system",system,"contract",contract),Object.class)));
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.getBytes(StandardCharsets.UTF_8)));
            Path file=directory.resolve(key+"-"+index+".json");
            if(mode.equals("replay")) {
                var recorded=json.readTree(Files.readString(file));
                boolean match=hash.equals(recorded.path("requestHash").asString());
                Files.writeString(root.resolve("replay-checks.jsonl"),json.writeValueAsString(Map.of("key",key,"call",index,"match",match,"actual",hash,"expected",recorded.path("requestHash").asString()))+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                if(!match) throw new IllegalStateException("Replay request hash differs: "+key);
                if(recorded.has("error")) throw new IllegalStateException("Recorded provider failure");
                var r=recorded.path("response");
                var blocks=new ArrayList<Block>();
                for(var block:r.path("message").path("content")) {
                    if(block.has("text")) blocks.add(new Text(block.path("text").asString()));
                    else if(block.has("input")) blocks.add(new ToolUse(block.path("id").asString(),block.path("name").asString(),json.convertValue(block.path("input"),Map.class)));
                    else throw new IllegalStateException("Unsupported recorded response block");
                }
                var usage=r.path("usage");
                return new Response(new Message(Role.valueOf(r.path("message").path("role").asString()),blocks),Outcome.valueOf(r.path("outcome").asString()),usage.isNull()?null:new Usage(usage.path("inputTokens").asLong(),usage.path("outputTokens").asLong()));
            }
            if(Files.exists(file)) throw new IllegalStateException("Refusing to overwrite recorded call");
            // Reserve a conservative maximum per request; abort before exceeding the approved $1 budget.
            if(spent+0.02>1 || errors>=3) throw new IllegalStateException("Evaluation cost/provider error circuit open");
            var record=new LinkedHashMap<String,Object>(); record.put("requestHash",hash); record.put("request",json.readTree(request));
            long started=System.nanoTime();
            try {
                Response response=invoke.get();
                record.put("response",response);
                if(response!=null && response.usage()!=null) spent+=response.usage().inputTokens()*0.10/1_000_000+response.usage().outputTokens()*0.50/1_000_000;
                else spent+=0.02;
                errors=0;
                return response;
            } catch(RuntimeException error) {
                errors++; spent+=0.02; record.put("error",error.getClass().getSimpleName()); throw error;
            } finally {
                record.put("durationMs",(System.nanoTime()-started)/1_000_000);record.put("budgetEstimateUsd",spent);
                Files.writeString(file,json.writeValueAsString(record));
                Files.writeString(root.resolve("budget.json"),json.writeValueAsString(Map.of("estimateUsd",spent,"consecutiveErrors",errors)));
            }
        } catch(java.io.IOException|java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("Evaluation tape I/O failure",e); }
    }
    private Object canonical(Object value) {
        if(value instanceof Map<?,?> map) {
            var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;
        }
        if(value instanceof List<?> list) return list.stream().map(this::canonical).toList();
        return value;
    }
}
