package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpSessionException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonObject;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class McpSessionManager {

    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

    public String createSession(JsonObject initParams) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new McpSession(id, initParams));
        return id;
    }

    public McpSession requireSession(String requestId, String sessionId) {
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            throw new McpSessionException(requestId, "Invalid or missing Mcp-Session-Id");
        }
        return sessions.get(sessionId);
    }

    public void terminateSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public int activeSessionCount() {
        return sessions.size();
    }
}
