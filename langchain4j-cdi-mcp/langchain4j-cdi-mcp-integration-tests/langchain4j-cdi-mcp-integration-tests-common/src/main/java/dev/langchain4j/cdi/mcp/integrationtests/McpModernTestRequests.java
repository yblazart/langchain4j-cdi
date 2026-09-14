package dev.langchain4j.cdi.mcp.integrationtests;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds MCP 2026-07-28 JSON-RPC bodies and matching HTTP headers. */
public final class McpModernTestRequests {

    private McpModernTestRequests() {}

    public static String meta(String version, String capabilitiesJson) {
        return "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"" + version + "\","
                + "\"io.modelcontextprotocol/clientInfo\":{\"name\":\"it-client\",\"version\":\"1.0\"},"
                + "\"io.modelcontextprotocol/clientCapabilities\":" + capabilitiesJson + "}";
    }

    /**
     * @param id JSON-RPC id, {@code null} for a notification
     * @param method method name
     * @param paramsJson params members without braces and without {@code _meta}, e.g. {@code "name":"greet"}; may be
     *     empty
     * @param capabilitiesJson client capabilities object
     * @return the request body
     */
    public static String body(Object id, String method, String paramsJson, String capabilitiesJson) {
        String params = "{" + (paramsJson.isEmpty() ? "" : paramsJson + ",")
                + meta(McpTestConstants.MODERN_VERSION, capabilitiesJson) + "}";
        String idPart = id == null ? "" : "\"id\":" + (id instanceof String ? "\"" + id + "\"" : id) + ",";
        return "{\"jsonrpc\":\"2.0\"," + idPart + "\"method\":\"" + method + "\",\"params\":" + params + "}";
    }

    public static Map<String, String> headers(String method, String name) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("MCP-Protocol-Version", McpTestConstants.MODERN_VERSION);
        headers.put("Mcp-Method", method);
        if (name != null) {
            headers.put("Mcp-Name", name);
        }
        headers.put("Accept", "application/json");
        return headers;
    }
}
