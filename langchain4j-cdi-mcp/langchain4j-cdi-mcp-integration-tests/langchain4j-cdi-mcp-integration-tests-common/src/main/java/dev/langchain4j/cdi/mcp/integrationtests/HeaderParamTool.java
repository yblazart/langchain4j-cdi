package dev.langchain4j.cdi.mcp.integrationtests;

import dev.langchain4j.cdi.mcp.server.api.McpHeader;
import jakarta.enterprise.context.ApplicationScoped;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/**
 * MCP tool carrying SEP-2243 {@code x-mcp-header} designations, so that the container-side validation of the
 * {@code Mcp-Param-<designation>} headers a client mirrors back can be exercised on every server under test.
 *
 * <p>The header lookup is the container's, so this is also what proves that the case-insensitive header matching
 * SEP-2243 requires holds on Quarkus, Helidon, WildFly and Open Liberty alike.
 */
@ApplicationScoped
public class HeaderParamTool {

    /** Name of the tool this fixture registers. */
    public static final String TENANT_ECHO = "tenantEcho";

    /** The {@code x-mcp-header} designation of the {@code tenant} argument. */
    public static final String TENANT_HEADER = "Tenant-Id";

    /** The {@code x-mcp-header} designation of the {@code attempt} argument. */
    public static final String ATTEMPT_HEADER = "Attempt";

    /** Creates a new instance. */
    public HeaderParamTool() {}

    /**
     * Echoes its two designated arguments.
     *
     * @param tenant a string argument mirrored into {@code Mcp-Param-Tenant-Id}
     * @param attempt an integer argument mirrored into {@code Mcp-Param-Attempt}
     * @return the two values, formatted
     */
    @Tool(name = TENANT_ECHO, description = "Echoes arguments designated with SEP-2243 x-mcp-header")
    public String tenantEcho(
            @ToolArg(name = "tenant", description = "Tenant identifier") @McpHeader(TENANT_HEADER) String tenant,
            @ToolArg(name = "attempt", description = "Attempt number") @McpHeader(ATTEMPT_HEADER) int attempt) {
        return "tenant=" + tenant + ", attempt=" + attempt;
    }
}
