package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpProtocolContext;
import dev.langchain4j.cdi.mcp.server.transport.McpResponseChannel;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Immutable context carrying per-request information for MCP framework type injection.
 *
 * @param sessionId the MCP session identifier
 * @param requestId the JSON-RPC request identifier
 * @param progressToken the progress token from request _meta (may be null)
 * @param cancelledFlag shared flag set to true when the request is cancelled
 * @param protocol the protocol era and metadata for this request, or {@code null} for legacy callers that predate it
 * @param clientRequester the requester used for client-bound interactions (elicitation, sampling, roots), or
 *     {@code null}
 * @param channel the response channel for request-scoped notifications (progress, logs), or {@code null}
 */
public record McpRequestContext(
        String sessionId,
        Object requestId,
        Object progressToken,
        AtomicBoolean cancelledFlag,
        McpProtocolContext protocol,
        McpClientRequester clientRequester,
        McpResponseChannel channel) {

    /**
     * Legacy constructor kept for backward compatibility.
     *
     * @param sessionId the MCP session identifier
     * @param requestId the JSON-RPC request identifier
     * @param progressToken the progress token from request _meta (may be null)
     * @param cancelledFlag shared flag set to true when the request is cancelled
     */
    public McpRequestContext(String sessionId, Object requestId, Object progressToken, AtomicBoolean cancelledFlag) {
        this(sessionId, requestId, progressToken, cancelledFlag, null, null, null);
    }

    /**
     * Returns whether this request uses MCP 2026-07-28 semantics.
     *
     * @return {@code true} if this request is modern
     */
    public boolean isModern() {
        return protocol != null && protocol.isModern();
    }
}
