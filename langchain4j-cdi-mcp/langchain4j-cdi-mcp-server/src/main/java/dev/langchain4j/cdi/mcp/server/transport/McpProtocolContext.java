package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Protocol information attached to a single request.
 *
 * @param era the protocol era
 * @param protocolVersion the negotiated protocol version
 * @param clientCapabilities client capabilities (modern: from {@code _meta}; legacy: empty)
 * @param clientInfo client implementation info, may be {@code null}
 * @param logLevel requested log level (modern only), {@code null} means no log notifications
 */
public record McpProtocolContext(
        McpEra era,
        String protocolVersion,
        JsonObject clientCapabilities,
        JsonObject clientInfo,
        McpLogLevel logLevel) {

    public static McpProtocolContext legacy(String protocolVersion) {
        return new McpProtocolContext(McpEra.LEGACY, protocolVersion, JsonValue.EMPTY_JSON_OBJECT, null, null);
    }

    public boolean isModern() {
        return era == McpEra.MODERN;
    }

    public boolean hasClientCapability(String name) {
        return clientCapabilities != null && clientCapabilities.containsKey(name);
    }
}
