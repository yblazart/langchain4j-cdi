package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpProtocolErrors;
import jakarta.json.JsonObject;
import java.time.Duration;
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
