package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.protocol.McpModelPreferences;
import dev.langchain4j.cdi.mcp.server.protocol.McpSamplingMessage;
import dev.langchain4j.cdi.mcp.server.transport.BatchRequestSpec;
import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpSamplingManager;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Implementation of {@link SamplingRequest} that delegates to {@link McpSamplingManager}. */
public class CdiSamplingRequest implements SamplingRequest {

    private final long maxTokens;
    private final List<McpSamplingMessage> messages;
    private final List<String> stopSequences;
    private final String systemPrompt;
    private final BigDecimal temperature;
    private final IncludeContext includeContext;
    private final McpModelPreferences modelPreferences;
    private final Map<String, Object> metadata;
    private final McpSamplingManager samplingManager;
    private final McpClientRequester requester;
    private final String key;

    CdiSamplingRequest(
            long maxTokens,
            List<McpSamplingMessage> messages,
            List<String> stopSequences,
            String systemPrompt,
            BigDecimal temperature,
            IncludeContext includeContext,
            McpModelPreferences modelPreferences,
            Map<String, Object> metadata,
            McpSamplingManager samplingManager,
            McpClientRequester requester,
            String key) {
        this.maxTokens = maxTokens;
        this.messages = messages;
        this.stopSequences = stopSequences;
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.includeContext = includeContext;
        this.modelPreferences = modelPreferences;
        this.metadata = metadata;
        this.samplingManager = samplingManager;
        this.requester = requester;
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
    public long maxTokens() {
        return maxTokens;
    }

    @Override
    public List<McpSamplingMessage> messages() {
        return messages;
    }

    @Override
    public List<String> stopSequences() {
        return stopSequences;
    }

    @Override
    public String systemPrompt() {
        return systemPrompt;
    }

    @Override
    public BigDecimal temperature() {
        return temperature;
    }

    @Override
    public IncludeContext includeContext() {
        return includeContext;
    }

    @Override
    public McpModelPreferences modelPreferences() {
        return modelPreferences;
    }

    @Override
    public Map<String, Object> metadata() {
        return metadata;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T send() {
        return (T) sendAndAwait();
    }

    @Override
    public SamplingResponse sendAndAwait() {
        requester.requireCapability("sampling");
        JsonObject result =
                samplingManager.createMessage(requester, messageMaps(), modelPrefsMap(), (int) maxTokens, key);
        if (result == null) {
            return null;
        }

        return new SamplingResponse(
                content(result.get("content")),
                result.getString("model", null),
                result.getString("role", null),
                result.getString("stopReason", null));
    }

    /**
     * Converts {@link #messages} into the plain-JSON list the wire schema expects.
     *
     * @return the messages, ready to embed in {@code messages}
     */
    List<Map<String, Object>> messageMaps() {
        return messages.stream()
                .map(m -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("role", m.role().toString());
                    map.put("content", m.content());
                    return map;
                })
                .toList();
    }

    /**
     * Converts {@link #modelPreferences} into the plain-JSON map the wire schema expects.
     *
     * @return the model preferences, ready to embed in {@code modelPreferences}, or {@code null}
     */
    Map<String, Object> modelPrefsMap() {
        if (modelPreferences == null) {
            return null;
        }
        Map<String, Object> modelPrefsMap = new LinkedHashMap<>();
        modelPrefsMap.put("hints", modelPreferences.hints());
        return modelPrefsMap;
    }

    /**
     * Unwraps the {@code content} of a {@code sampling/createMessage} result. A content block stays a
     * {@link JsonObject} (it is itself a {@code Map<String, JsonValue>}); a bare string is unwrapped to a
     * {@link String} so a caller never has to unquote it.
     *
     * <p>Package-visible so {@link CdiInteractionResults} can reuse it for a batched sampling answer.
     *
     * @param content the raw {@code content} value, or {@code null} when absent
     * @return the content to expose on the {@link SamplingResponse}, or {@code null}
     */
    static Object content(JsonValue content) {
        if (content == null || content.getValueType() == JsonValue.ValueType.NULL) {
            return null;
        }
        return content instanceof JsonString s ? s.getString() : content;
    }

    /**
     * Builds the batch spec for this request, under the given batch key (MRTR batch, SEP-2322): the batch key is
     * authoritative, so a request that also carries its own, different {@code Builder.setKey} value is rejected.
     *
     * @param batchKey the key {@link McpInteractions.Batch#sample(String, SamplingRequest)} was called with
     * @return the batch spec, ready to hand to {@code McpClientRequester.requestBatch}
     * @throws IllegalArgumentException if this request's own key conflicts with {@code batchKey}
     */
    BatchRequestSpec toBatchSpec(String batchKey) {
        if (key != null && !key.equals(batchKey)) {
            throw new IllegalArgumentException(
                    "Batch.sample: request's own key '" + key + "' conflicts with batch key '" + batchKey
                            + "'; the batch key is authoritative, so use the same key or omit setKey on the request");
        }
        Map<String, Object> params = McpSamplingManager.buildParams(messageMaps(), modelPrefsMap(), (int) maxTokens);
        return new BatchRequestSpec(batchKey, "sampling/createMessage", params);
    }

    static class CdiBuilder implements SamplingRequest.Builder {

        private final McpSamplingManager samplingManager;
        private final McpClientRequester requester;
        private long maxTokens = 1024;
        private final List<McpSamplingMessage> messages = new ArrayList<>();
        private List<String> stopSequences = List.of();
        private String systemPrompt;
        private BigDecimal temperature;
        private IncludeContext includeContext;
        private McpModelPreferences modelPreferences;
        private Map<String, Object> metadata = Map.of();
        private String key;

        CdiBuilder(McpSamplingManager samplingManager, McpClientRequester requester) {
            this.samplingManager = samplingManager;
            this.requester = requester;
        }

        @Override
        public Builder addMessage(McpSamplingMessage message) {
            messages.add(message);
            return this;
        }

        @Override
        public Builder setMaxTokens(long maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        @Override
        public Builder setTemperature(BigDecimal temperature) {
            this.temperature = temperature;
            return this;
        }

        @Override
        public Builder setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        @Override
        public Builder setIncludeContext(IncludeContext includeContext) {
            this.includeContext = includeContext;
            return this;
        }

        @Override
        public Builder setModelPreferences(McpModelPreferences modelPreferences) {
            this.modelPreferences = modelPreferences;
            return this;
        }

        @Override
        public Builder setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        @Override
        public Builder setStopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        @Override
        public Builder setTimeout(Duration timeout) {
            // timeout is not directly used in our implementation
            return this;
        }

        @Override
        public Builder setKey(String key) {
            this.key = key;
            return this;
        }

        @Override
        public SamplingRequest build() {
            if (messages.isEmpty()) {
                throw new IllegalStateException("At least one sampling message is required");
            }
            if (maxTokens <= 0) {
                throw new IllegalStateException("maxTokens must be positive");
            }
            if (key != null && key.isBlank()) {
                throw new IllegalArgumentException("SamplingRequest.Builder.setKey: key must not be blank");
            }
            return new CdiSamplingRequest(
                    maxTokens,
                    List.copyOf(messages),
                    List.copyOf(stopSequences),
                    systemPrompt,
                    temperature,
                    includeContext,
                    modelPreferences,
                    Map.copyOf(metadata),
                    samplingManager,
                    requester,
                    key);
        }
    }
}
