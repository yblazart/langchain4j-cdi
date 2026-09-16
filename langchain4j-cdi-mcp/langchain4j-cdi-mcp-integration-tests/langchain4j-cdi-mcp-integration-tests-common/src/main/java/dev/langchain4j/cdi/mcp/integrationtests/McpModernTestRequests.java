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

    /**
     * Builds the body of a {@code tools/call} for {@link HeaderParamTool#TENANT_ECHO}, whose two arguments carry
     * SEP-2243 {@code x-mcp-header} designations.
     *
     * @param id the JSON-RPC id
     * @return the request body, always carrying {@code tenant=acme} and {@code attempt=7}
     */
    public static String designatedCallBody(Object id) {
        return body(
                id,
                "tools/call",
                "\"name\":\"" + HeaderParamTool.TENANT_ECHO + "\",\"arguments\":{\"tenant\":\"acme\",\"attempt\":7}",
                "{}");
    }

    /**
     * Builds the headers of a {@code tools/call} for {@link HeaderParamTool#TENANT_ECHO}, adding the given extra header
     * name/value pairs verbatim so that a test can spell an {@code Mcp-Param-*} name in any case it likes.
     *
     * @param extra header name/value pairs, of even length
     * @return the headers
     */
    public static Map<String, String> designatedCallHeaders(String... extra) {
        Map<String, String> headers = headers("tools/call", HeaderParamTool.TENANT_ECHO);
        for (int i = 0; i < extra.length; i += 2) {
            headers.put(extra[i], extra[i + 1]);
        }
        return headers;
    }

    /** The three malformed {@code =?base64?…?=} payloads every container suite checks answer 400 rather than 500. */
    public static String[] malformedBase64Values() {
        return new String[] {"=?base64?YWNtZQ?=", "=?base64?YWN!!tZQ==?=", "=?base64?YWNtZQ====?="};
    }
}
