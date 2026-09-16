package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpProtocolErrors;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stateless MRTR requester: answers from the collected input responses, otherwise signals that input is needed. */
public class McpReplayClientRequester implements McpClientRequester {

    private final McpProtocolContext protocol;
    private final Map<String, JsonObject> responses;
    private int next;

    /**
     * Creates a requester bound to the given protocol context and the input responses collected so far.
     *
     * @param protocol the protocol context for this request
     * @param responses previously collected input responses, keyed by call order ({@code input-0}, {@code input-1}, …)
     */
    public McpReplayClientRequester(McpProtocolContext protocol, Map<String, JsonObject> responses) {
        this.protocol = protocol;
        this.responses = responses;
    }

    @Override
    public boolean supports(String capability) {
        return protocol.hasClientCapability(capability);
    }

    @Override
    public JsonObject request(String method, Map<String, Object> params, Duration timeout) {
        String key = "input-" + next++;
        JsonObject response = responses.get(key);
        if (response != null) {
            return response;
        }
        throw new McpInputRequiredSignal(key, method, params);
    }

    /**
     * Sends a request under the given key, or falls back to the unchanged auto-numbered behaviour of
     * {@link #request(String, Map, Duration)} when {@code key} is {@code null}. A keyed call never advances the
     * auto-numbering counter.
     */
    @Override
    public JsonObject request(String method, Map<String, Object> params, Duration timeout, String key) {
        if (key == null) {
            return request(method, params, timeout);
        }
        JsonObject response = responses.get(key);
        if (response != null) {
            return response;
        }
        throw new McpInputRequiredSignal(key, method, params);
    }

    /**
     * Answers every batch member already present in the collected responses, and reports every other member in a single
     * {@link McpInputRequiredBatchSignal} — never just the first missing one, which is the whole point of a batch under
     * a stateless (REPLAY) requester.
     */
    @Override
    public Map<String, JsonObject> requestBatch(List<BatchRequestSpec> requests, Duration timeout) {
        Map<String, JsonObject> collected = new LinkedHashMap<>();
        List<McpInputRequiredSignal> missing = new ArrayList<>();
        for (BatchRequestSpec spec : requests) {
            JsonObject response = responses.get(spec.key());
            if (response != null) {
                collected.put(spec.key(), response);
            } else {
                missing.add(new McpInputRequiredSignal(spec.key(), spec.method(), spec.params()));
            }
        }
        if (!missing.isEmpty()) {
            throw new McpInputRequiredBatchSignal(missing);
        }
        return collected;
    }

    @Override
    public boolean isModern() {
        return true;
    }

    @Override
    public void requireCapability(String capability) {
        if (!supports(capability)) {
            throw McpProtocolErrors.missingClientCapability(null, capability);
        }
    }
}
