package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.transport.BatchRequestSpec;
import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Implementation of {@link McpInteractions.Batch} that delegates to {@link McpClientRequester#requestBatch}. */
class CdiBatch implements McpInteractions.Batch {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final McpClientRequester requester;
    private final List<BatchRequestSpec> specs = new ArrayList<>();
    private final Set<String> keys = new LinkedHashSet<>();
    private final Set<String> capabilitiesNeeded = new LinkedHashSet<>();

    CdiBatch(McpClientRequester requester) {
        this.requester = requester;
    }

    @Override
    public McpInteractions.Batch elicit(String key, ElicitationRequest request) {
        requireKey(key, "elicit");
        if (!(request instanceof CdiElicitationRequest cdiRequest)) {
            throw new IllegalArgumentException("Batch.elicit: request must be built via Elicitation.requestBuilder()");
        }
        addSpec(cdiRequest.toBatchSpec(key));
        capabilitiesNeeded.add("elicitation");
        return this;
    }

    @Override
    public McpInteractions.Batch sample(String key, SamplingRequest request) {
        requireKey(key, "sample");
        if (!(request instanceof CdiSamplingRequest cdiRequest)) {
            throw new IllegalArgumentException("Batch.sample: request must be built via Sampling.requestBuilder()");
        }
        addSpec(cdiRequest.toBatchSpec(key));
        capabilitiesNeeded.add("sampling");
        return this;
    }

    @Override
    public McpInteractions.Batch roots(String key) {
        requireKey(key, "roots");
        addSpec(new BatchRequestSpec(key, "roots/list", Map.of()));
        capabilitiesNeeded.add("roots");
        return this;
    }

    @Override
    public McpInteractionResults awaitAll() {
        if (specs.isEmpty()) {
            throw new IllegalStateException("Batch.awaitAll: batch must declare at least one interaction");
        }
        // fail the whole batch up front on a missing capability, the same way a single interaction fails today
        for (String capability : capabilitiesNeeded) {
            requester.requireCapability(capability);
        }
        Map<String, JsonObject> results = requester.requestBatch(List.copyOf(specs), TIMEOUT);
        return new CdiInteractionResults(results, List.copyOf(specs));
    }

    private void requireKey(String key, String member) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Batch." + member + ": key must not be blank");
        }
    }

    private void addSpec(BatchRequestSpec spec) {
        if (!keys.add(spec.key())) {
            throw new IllegalArgumentException(
                    "Batch: duplicate key '" + spec.key() + "'; batch keys must be unique within a batch");
        }
        specs.add(spec);
    }
}
