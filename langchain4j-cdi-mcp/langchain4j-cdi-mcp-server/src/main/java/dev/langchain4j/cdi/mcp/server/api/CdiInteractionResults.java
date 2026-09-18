package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.protocol.McpRoot;
import dev.langchain4j.cdi.mcp.server.transport.BatchRequestSpec;
import dev.langchain4j.cdi.mcp.server.transport.McpRootsManager;
import jakarta.json.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Implementation of {@link McpInteractionResults} backed by the answers a batch collected. */
class CdiInteractionResults implements McpInteractionResults {

    private final Map<String, JsonObject> results;
    private final Map<String, BatchRequestSpec> specsByKey;

    CdiInteractionResults(Map<String, JsonObject> results, List<BatchRequestSpec> specs) {
        if (results.size() != specs.size()) {
            throw new IllegalStateException("Batch collected " + results.size() + " answers but declared "
                    + specs.size() + " requests; partial results indicate a transport failure");
        }
        this.results = results;
        Map<String, BatchRequestSpec> byKey = new LinkedHashMap<>();
        specs.forEach(spec -> byKey.put(spec.key(), spec));
        this.specsByKey = byKey;
    }

    @Override
    public ElicitationResponse elicitation(String key) {
        return new CdiElicitationResponse(require(key, "elicitation/create"));
    }

    @Override
    public SamplingResponse sampling(String key) {
        JsonObject result = require(key, "sampling/createMessage");
        return new SamplingResponse(
                CdiSamplingRequest.content(result.get("content")),
                result.getString("model", null),
                result.getString("role", null),
                result.getString("stopReason", null));
    }

    @Override
    public List<McpRoot> roots(String key) {
        return McpRootsManager.parseRoots(require(key, "roots/list"));
    }

    private JsonObject require(String key, String expectedMethod) {
        BatchRequestSpec spec = specsByKey.get(key);
        if (spec == null) {
            throw new IllegalArgumentException("McpInteractionResults: unknown key '" + key + "'");
        }
        if (!spec.method().equals(expectedMethod)) {
            throw new IllegalArgumentException("McpInteractionResults: key '" + key + "' was declared as "
                    + spec.method() + ", not " + expectedMethod);
        }
        JsonObject result = results.get(key);
        if (result == null) {
            throw new IllegalStateException("McpInteractionResults: no answer collected for key '" + key + "'");
        }
        return result;
    }
}
