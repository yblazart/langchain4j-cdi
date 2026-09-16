package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Allows the server to request user input (elicitation) from a connected client. Sends an {@code elicitation/create}
 * request via SSE and waits for the client's response.
 */
@ApplicationScoped
public class McpElicitationManager {

    private static final Logger LOGGER = Logger.getLogger(McpElicitationManager.class.getName());

    /** Creates a new elicitation manager. */
    public McpElicitationManager() {}

    @Inject
    McpServerRequestManager requestManager;

    /**
     * Sends an elicitation request to the client.
     *
     * @param sessionId the target session
     * @param message the message to display to the user
     * @param requestedSchema the JSON schema for the expected response
     * @param timeoutSeconds how long to wait for the response
     * @return the client's response as a JsonObject, or null on timeout/error
     * @deprecated use {@link #createElicitation(McpClientRequester, String, Map, long)}
     */
    @Deprecated
    public JsonObject createElicitation(
            String sessionId, String message, Map<String, Object> requestedSchema, long timeoutSeconds) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message", message);
        if (requestedSchema != null) {
            params.put("requestedSchema", requestedSchema);
        }

        JsonObject result = requestManager.sendRequest(sessionId, "elicitation/create", params, timeoutSeconds);
        if (result != null) {
            LOGGER.fine("MCP: Received elicitation response from session " + sessionId);
        }
        return result;
    }

    /**
     * Sends an elicitation request to the client via the given requester.
     *
     * @param requester the client requester to send the request through
     * @param message the message to display to the user
     * @param properties the JSON schema properties for the expected response
     * @param timeoutSeconds how long to wait for the response (ignored by stateless requesters)
     * @return the client's response as a JsonObject, or null on timeout/error
     */
    public JsonObject createElicitation(
            McpClientRequester requester, String message, Map<String, Object> properties, long timeoutSeconds) {
        return createElicitation(requester, message, properties, timeoutSeconds, null);
    }

    /**
     * Sends an elicitation request to the client via the given requester, under the given key (MRTR, SEP-2322).
     *
     * @param requester the client requester to send the request through
     * @param message the message to display to the user
     * @param properties the JSON schema properties for the expected response
     * @param timeoutSeconds how long to wait for the response (ignored by stateless requesters)
     * @param key the key to emit the request under, or {@code null} to let the server assign one
     * @return the client's response as a JsonObject, or null on timeout/error
     */
    public JsonObject createElicitation(
            McpClientRequester requester,
            String message,
            Map<String, Object> properties,
            long timeoutSeconds,
            String key) {
        Map<String, Object> params = buildParams(requester.isModern(), message, properties);
        return requester.request("elicitation/create", params, Duration.ofSeconds(timeoutSeconds), key);
    }

    /**
     * Builds the {@code elicitation/create} params, shared between the direct send path and MRTR batch construction.
     *
     * @param modern whether the requester uses MCP 2026-07-28 semantics
     * @param message the message to display to the user
     * @param properties the JSON schema properties for the expected response
     * @return the request params
     */
    public static Map<String, Object> buildParams(boolean modern, String message, Map<String, Object> properties) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (modern) {
            params.put("mode", "form");
        }
        params.put("message", message);
        params.put(
                "requestedSchema", Map.of("type", "object", "properties", properties != null ? properties : Map.of()));
        return params;
    }

    /**
     * Returns the server request manager used for legacy client requests.
     *
     * @return the server request manager
     */
    public McpServerRequestManager getRequestManager() {
        return requestManager;
    }
}
