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
     * Returns the session id.
     *
     * @return the session id, or {@code null}
     */
    public String sessionId() {
        return session != null ? session.getId() : null;
    }
}
