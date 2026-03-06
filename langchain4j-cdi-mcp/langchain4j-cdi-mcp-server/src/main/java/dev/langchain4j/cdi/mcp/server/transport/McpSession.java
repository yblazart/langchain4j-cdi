package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.json.JsonObject;
import java.time.Instant;

public class McpSession {

    private final String id;
    private final Instant createdAt;
    private final JsonObject clientCapabilities;
    private volatile boolean initialized;

    public McpSession(String id, JsonObject clientCapabilities) {
        this.id = id;
        this.createdAt = Instant.now();
        this.clientCapabilities = clientCapabilities;
        this.initialized = false;
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public JsonObject getClientCapabilities() {
        return clientCapabilities;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        this.initialized = true;
    }
}
