package dev.langchain4j.cdi.mcp.server.protocol;

/** Reserved {@code _meta} keys defined by MCP 2026-07-28. */
public final class McpMetaKeys {

    public static final String META = "_meta";
    public static final String PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    public static final String CLIENT_INFO = "io.modelcontextprotocol/clientInfo";
    public static final String CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
    public static final String LOG_LEVEL = "io.modelcontextprotocol/logLevel";
    public static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";
    public static final String SUBSCRIPTION_ID = "io.modelcontextprotocol/subscriptionId";

    private McpMetaKeys() {}
}
