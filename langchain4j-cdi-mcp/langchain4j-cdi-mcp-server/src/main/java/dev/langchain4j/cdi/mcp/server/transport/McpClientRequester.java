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
