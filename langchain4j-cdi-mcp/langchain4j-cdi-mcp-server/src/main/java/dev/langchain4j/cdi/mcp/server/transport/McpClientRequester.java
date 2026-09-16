package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.Map;

/** Performs a client interaction (elicitation, sampling, roots) on behalf of a running server method. */
public interface McpClientRequester {

    /**
     * Returns whether the client declared a given capability.
     *
     * @param capability client capability name, e.g. {@code elicitation}
     * @return whether the client declared this capability
     */
    boolean supports(String capability);

    /**
     * Sends a request to the client and returns its result.
     *
     * @param method JSON-RPC method, e.g. {@code elicitation/create}
     * @param params request params
     * @param timeout maximum wait (ignored by stateless implementations)
     * @return the client result, or {@code null} on timeout (legacy)
     */
    JsonObject request(String method, Map<String, Object> params, Duration timeout);

    /**
     * Sends a request to the client under a caller-chosen key (MRTR, SEP-2322): the key the request is emitted under in
     * {@code inputRequests}, and the key its answer is looked up by in {@code inputResponses}. The default delegates to
     * {@link #request(String, Map, Duration)}, silently ignoring {@code key} — implementations that support MRTR
     * override this method; a {@code null} key means "let the server assign one by call order", exactly
     * {@link #request(String, Map, Duration)}'s existing behaviour.
     *
     * @param method JSON-RPC method, e.g. {@code elicitation/create}
     * @param params request params
     * @param timeout maximum wait (ignored by stateless implementations)
     * @param key the key to emit the request under, or {@code null} to let the server assign one
     * @return the client result, or {@code null} on timeout (legacy)
     */
    default JsonObject request(String method, Map<String, Object> params, Duration timeout, String key) {
        return request(method, params, timeout);
    }

    /**
     * Returns whether requests are issued with MCP 2026-07-28 semantics.
     *
     * @return {@code true} for modern requests
     */
    default boolean isModern() {
        return false;
    }

    /**
     * Fails if the client did not declare the capability. Legacy clients are not checked (historical behaviour).
     *
     * @param capability capability name
     */
    default void requireCapability(String capability) {}
}
