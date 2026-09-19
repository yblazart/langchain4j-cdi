/**
 * Stand-in for a downstream application compiled on a strict module path against the MCP server module.
 *
 * <p>This module is never packaged nor deployed: it exists only so that the {@code jlink-vidocq} profile can prove, at
 * build time, that every type {@code langchain4j-cdi-mcp/README.md} documents as user-facing is actually
 * <em>readable</em> by a consumer module. Reflection-only access (the MCP server is an {@code open module}) is not
 * enough here — {@code javac} needs real {@code exports}. It also proves, by running {@code McpServerSpiBinding}, that
 * the MCP server's {@code McpServerSPI} provider is bound on the module path, which only a {@code provides} clause
 * does.
 */
module dev.langchain4j.cdi.mcp.modulepathconsumer {
    requires dev.langchain4j.cdi.mcp.server;
    requires jakarta.cdi;
    requires jakarta.inject;

    uses org.mcpjava.server.spi.McpServerSPI;
}
