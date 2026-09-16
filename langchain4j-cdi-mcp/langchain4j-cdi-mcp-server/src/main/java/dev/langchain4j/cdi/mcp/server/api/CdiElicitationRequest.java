package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.transport.BatchRequestSpec;
import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpElicitationManager;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Implementation of {@link ElicitationRequest} that delegates to {@link McpElicitationManager}. */
public class CdiElicitationRequest implements ElicitationRequest {

    private final String message;
    private final Map<String, PrimitiveSchema> requestedSchema;
    private final McpElicitationManager elicitationManager;
    private final McpClientRequester requester;
    private final long timeoutSeconds;
    private final String key;

    CdiElicitationRequest(
            String message,
            Map<String, PrimitiveSchema> requestedSchema,
            McpElicitationManager elicitationManager,
            McpClientRequester requester,
            long timeoutSeconds,
            String key) {
        this.message = message;
        this.requestedSchema = requestedSchema;
        this.elicitationManager = elicitationManager;
        this.requester = requester;
        this.timeoutSeconds = timeoutSeconds;
        this.key = key;
    }

    /**
     * Returns the key this request was configured with via {@link Builder#setKey(String)}.
     *
     * @return the configured key, or {@code null} if none was set
     */
    String key() {
        return key;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public Map<String, PrimitiveSchema> requestedSchema() {
        return requestedSchema;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T send() {
        return (T) sendAndAwait();
    }

    @Override
    public ElicitationResponse sendAndAwait() {
        requester.requireCapability("elicitation");
        Map<String, Object> schemaMap = schemaMap();
        JsonObject result = elicitationManager.createElicitation(requester, message, schemaMap, timeoutSeconds, key);
        return result == null ? null : new CdiElicitationResponse(result);
    }

    /**
     * Converts {@link #requestedSchema} into the plain-JSON map the wire schema expects.
     *
     * @return the schema properties, ready to embed in {@code requestedSchema.properties}
     */
    Map<String, Object> schemaMap() {
        Map<String, Object> schemaMap = new LinkedHashMap<>();
        if (requestedSchema != null) {
            requestedSchema.forEach((name, schema) -> schemaMap.put(name, schema.asJson()));
        }
        return schemaMap;
    }

    /**
     * Builds the batch spec for this request, under the given batch key (MRTR batch, SEP-2322): the batch key is
     * authoritative, so a request that also carries its own, different {@code Builder.setKey} value is rejected.
     *
     * @param batchKey the key {@link McpInteractions.Batch#elicit(String, ElicitationRequest)} was called with
     * @return the batch spec, ready to hand to {@code McpClientRequester.requestBatch}
     * @throws IllegalArgumentException if this request's own key conflicts with {@code batchKey}
     */
    BatchRequestSpec toBatchSpec(String batchKey) {
        if (key != null && !key.equals(batchKey)) {
            throw new IllegalArgumentException(
                    "Batch.elicit: request's own key '" + key + "' conflicts with batch key '" + batchKey
                            + "'; the batch key is authoritative, so use the same key or omit setKey on the request");
        }
        Map<String, Object> params = McpElicitationManager.buildParams(requester.isModern(), message, schemaMap());
        return new BatchRequestSpec(batchKey, "elicitation/create", params);
    }

    static class CdiBuilder implements ElicitationRequest.Builder {

        private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

        private final McpElicitationManager elicitationManager;
        private final McpClientRequester requester;
        private String message;
        private final Map<String, PrimitiveSchema> requestedSchema = new LinkedHashMap<>();
        private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private String key;

        CdiBuilder(McpElicitationManager elicitationManager, McpClientRequester requester) {
            this.elicitationManager = elicitationManager;
            this.requester = requester;
        }

        @Override
        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        @Override
        public Builder addSchemaProperty(String name, PrimitiveSchema schema) {
            this.requestedSchema.put(name, schema);
            return this;
        }

        @Override
        public Builder setTimeout(Duration timeout) {
            if (timeout != null && timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must not be negative");
            }
            this.timeoutSeconds = timeout != null ? timeout.toSeconds() : DEFAULT_TIMEOUT_SECONDS;
            return this;
        }

        @Override
        public Builder setKey(String key) {
            this.key = key;
            return this;
        }

        @Override
        public ElicitationRequest build() {
            if (key != null && key.isBlank()) {
                throw new IllegalArgumentException("ElicitationRequest.Builder.setKey: key must not be blank");
            }
            return new CdiElicitationRequest(
                    message, Map.copyOf(requestedSchema), elicitationManager, requester, timeoutSeconds, key);
        }
    }
}
