package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpProtocolErrors;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Stateful MRTR requester: blocks the invocation thread until the client answers on a retry. */
public class McpContinuationClientRequester implements McpClientRequester {

    private final McpProtocolContext protocol;
    private final McpContinuation continuation;

    /**
     * Creates a requester bound to the given protocol context and continuation.
     *
     * @param protocol the protocol context for this request
     * @param continuation the continuation the invocation runs under
     */
    public McpContinuationClientRequester(McpProtocolContext protocol, McpContinuation continuation) {
        this.protocol = protocol;
        this.continuation = continuation;
    }

    @Override
    public boolean supports(String capability) {
        return protocol.hasClientCapability(capability);
    }

    @Override
    public JsonObject request(String method, Map<String, Object> params, Duration timeout) {
        return continuation.awaitInput(method, params);
    }

    @Override
    public JsonObject request(String method, Map<String, Object> params, Duration timeout, String key) {
        return continuation.awaitInput(method, params, key);
    }

    /** Parks the worker thread until every batch member is answered (MRTR CONTINUATION mode, SEP-2322). */
    @Override
    public Map<String, JsonObject> requestBatch(List<BatchRequestSpec> requests, Duration timeout) {
        List<McpContinuation.PendingRequest> pendingRequests = requests.stream()
                .map(spec -> new McpContinuation.PendingRequest(spec.key(), spec.method(), spec.params()))
                .toList();
        return continuation.awaitBatch(pendingRequests);
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
