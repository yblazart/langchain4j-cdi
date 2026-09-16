package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import dev.langchain4j.cdi.mcp.server.logging.McpLogger;
import dev.langchain4j.cdi.mcp.server.transport.McpProtocolContext;
import dev.langchain4j.cdi.mcp.server.transport.McpSession;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Implementation of {@link McpConnection} that delegates to {@link McpSession} and {@link McpLogger}, or, for modern
 * requests, to the per-request {@link McpProtocolContext}.
 */
public class CdiMcpConnection implements McpConnection {

    private final McpSession session;
    private final McpLogger mcpLogger;
    private final McpProtocolContext protocol;
    private final Object requestId;

    /**
     * Creates a new MCP connection wrapper.
     *
     * @param session the MCP session
     * @param mcpLogger the MCP logger
     */
    public CdiMcpConnection(McpSession session, McpLogger mcpLogger) {
        this.session = session;
        this.mcpLogger = mcpLogger;
        this.protocol = null;
        this.requestId = null;
    }

    /**
     * Creates a new MCP connection wrapper for a modern, sessionless request.
     *
     * @param protocol the protocol context of the current request
     * @param requestId the JSON-RPC request identifier
     */
    public CdiMcpConnection(McpProtocolContext protocol, Object requestId) {
        this.session = null;
        this.mcpLogger = null;
        this.protocol = protocol;
        this.requestId = requestId;
    }

    @Override
    public String id() {
        return session != null ? session.getId() : "request-" + requestId;
    }

    @Override
    public Status status() {
        if (session == null) {
            return Status.IN_OPERATION;
        }
        return session.isInitialized() ? Status.IN_OPERATION : Status.INITIALIZING;
    }

    /**
     * Returns the MCP client's advertised capabilities from the {@code initialize} handshake, or, for modern requests,
     * the current request's protocol metadata.
     *
     * @return the client capabilities object, or {@code null} if the session has not yet been initialized
     */
    @Override
    public JsonObject initialRequest() {
        if (session != null) {
            return session.getClientCapabilities();
        }
        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("protocolVersion", protocol.protocolVersion())
                .add("capabilities", protocol.clientCapabilities());
        if (protocol.clientInfo() != null) {
            builder.add("clientInfo", protocol.clientInfo());
        }
        return builder.build();
    }

    @Override
    public McpLog.LogLevel logLevel() {
        McpLogLevel level = session != null
                ? mcpLogger.getMinimumLevel()
                : (protocol.logLevel() != null ? protocol.logLevel() : McpLogLevel.emergency);
        return McpLog.LogLevel.values()[level.ordinal()];
    }
}
