package dev.langchain4j.cdi.mcp.server.error;

import dev.langchain4j.cdi.mcp.server.protocol.McpProtocolVersions;
import java.util.LinkedHashMap;
import java.util.Map;

/** Factories for protocol errors introduced by MCP 2026-07-28. */
public final class McpProtocolErrors {

    private McpProtocolErrors() {}

    /**
     * Creates an MCP exception for HTTP header validation failures.
     *
     * @param requestId the JSON-RPC request ID
     * @param detail the description of the header mismatch
     * @return an MCP exception with HTTP status 400
     */
    public static McpException headerMismatch(Object requestId, String detail) {
        return new McpException(requestId, McpErrorCode.HEADER_MISMATCH, "Header mismatch: " + detail, 400, null);
    }

    /**
     * Creates an MCP exception for unsupported protocol versions.
     *
     * @param requestId the JSON-RPC request ID
     * @param requested the protocol version that was requested
     * @return an MCP exception with HTTP status 400 and supported versions in data
     */
    public static McpException unsupportedProtocolVersion(Object requestId, String requested) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("supported", McpProtocolVersions.SUPPORTED);
        data.put("requested", requested);
        return new McpException(
                requestId, McpErrorCode.UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version", 400, data);
    }

    /**
     * Creates an MCP exception for missing required client capabilities.
     *
     * @param requestId the JSON-RPC request ID
     * @param capability the capability that is required but missing
     * @return an MCP exception with HTTP status 400 and capability info in data
     */
    public static McpException missingClientCapability(Object requestId, String capability) {
        return new McpException(
                requestId,
                McpErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY,
                "Missing required client capability: " + capability,
                400,
                Map.of("requiredCapabilities", Map.of(capability, Map.of())));
    }

    /**
     * Creates an MCP exception for unknown methods.
     *
     * @param requestId the JSON-RPC request ID
     * @param method the method name that was not found
     * @return an MCP exception with HTTP status 404
     */
    public static McpException methodNotFound(Object requestId, String method) {
        return new McpException(requestId, McpErrorCode.METHOD_NOT_FOUND, "Unknown method: " + method, 404, null);
    }

    /**
     * Creates an MCP exception for invalid method parameters.
     *
     * @param requestId the JSON-RPC request ID
     * @param detail the description of the parameter issue
     * @return an MCP exception with HTTP status 400
     */
    public static McpException invalidParams(Object requestId, String detail) {
        return new McpException(requestId, McpErrorCode.INVALID_PARAMS, detail, 400, null);
    }
}
