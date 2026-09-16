package dev.langchain4j.cdi.mcp.conformance;

import dev.langchain4j.cdi.mcp.server.transport.McpMrtrMode;
import dev.langchain4j.cdi.mcp.server.transport.McpServerConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import java.time.Duration;

/** Server configuration for the conformance fixture server. */
@ApplicationScoped
public class ConformanceServerConfig {

    /** Creates a new instance. */
    public ConformanceServerConfig() {}

    /**
     * Produces the MCP server configuration.
     *
     * @return the server configuration
     */
    @Produces
    @Named("mcp-server")
    @ApplicationScoped
    public McpServerConfig mcpServerConfig() {
        return McpServerConfig.builder()
                .serverName("langchain4j-cdi-mcp-conformance")
                .serverVersion("1.0.0")
                .mrtrMode(McpMrtrMode.REPLAY)
                .requestStateSecret("langchain4j-cdi-mcp-conformance-fixed-request-state-secret")
                .requestStateTtl(Duration.ofMinutes(10))
                .build();
    }
}
