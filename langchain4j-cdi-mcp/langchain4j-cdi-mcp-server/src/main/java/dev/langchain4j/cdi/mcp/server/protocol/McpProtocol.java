package dev.langchain4j.cdi.mcp.server.protocol;

public final class McpProtocol {

    /** @deprecated use {@link McpProtocolVersions#LEGACY_2025_03_26} or {@link McpProtocolVersions#SUPPORTED}. */
    @Deprecated
    public static final String VERSION = McpProtocolVersions.LEGACY_2025_03_26;

    private McpProtocol() {}
}
