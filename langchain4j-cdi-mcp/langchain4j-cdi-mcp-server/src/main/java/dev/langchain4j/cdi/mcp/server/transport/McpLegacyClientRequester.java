package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.Map;

/** Legacy era: sends a server-initiated JSON-RPC request on the session GET stream and waits for the reply. */
public class McpLegacyClientRequester implements McpClientRequester {

    private final McpSession session;
    private final McpServerRequestManager requestManager;

    /**
     * Creates a requester bound to a session.
     *
     * @param session the MCP session, or {@code null}
     * @param requestManager the server request manager used to send the request
     */
    public McpLegacyClientRequester(McpSession session, McpServerRequestManager requestManager) {
        this.session = session;
        this.requestManager = requestManager;
    }

    @Override
    public boolean supports(String capability) {
        return session != null && session.hasCapability(capability);
    }

    @Override
    public JsonObject request(String method, Map<String, Object> params, Duration timeout) {
        return requestManager.sendRequest(sessionId(), method, params, timeout.toSeconds());
    }

    /**
     * Legacy (2025-03-26) has no MRTR: the request is sent and answered directly, so a key is meaningless here and is
     * ignored.
     */
    @Override
    public JsonObject request(String method, Map<String, Object> params, Duration timeout, String key) {
        return request(method, params, timeout);
    }

    /**
     * Returns the session id.
     *
     * @return the session id, or {@code null}
     */
    public String sessionId() {
        return session != null ? session.getId() : null;
    }
}
