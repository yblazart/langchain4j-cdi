package dev.langchain4j.cdi.mcp.server.protocol;

/** HTTP header names used by the Streamable HTTP transport. */
public final class McpHttpHeaders {

    public static final String PROTOCOL_VERSION = "MCP-Protocol-Version";
    public static final String METHOD = "Mcp-Method";
    public static final String NAME = "Mcp-Name";
    public static final String SESSION_ID = "Mcp-Session-Id";
    public static final String ORIGIN = "Origin";
    public static final String HOST = "Host";

    private McpHttpHeaders() {}
}
