package dev.langchain4j.cdi.mcp.modulepathconsumer;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.mcpjava.server.spi.McpServerSPI;

/**
 * Step (5) of the gate: the MCP framework's {@code McpServerSPILoader} must find langchain4j-cdi's implementation
 * when everything runs on the module path.
 *
 * <p>On the class path the provider comes from {@code META-INF/services}; on the module path only a {@code provides}
 * clause counts, and the class-path integration suites cannot see the difference. This runs on the same strict module
 * path as steps (3) and (4) and exits non-zero when the binding is missing. It reads the provider types without
 * instantiating them, so it depends on nothing but the module descriptors.
 */
public final class McpServerSpiBinding {

    private static final String EXPECTED = "dev.langchain4j.cdi.mcp.server.spi.CdiMcpServerSPI";

    private McpServerSpiBinding() {}

    public static void main(String[] args) {
        List<String> providers = ServiceLoader.load(McpServerSPI.class).stream()
                .map(provider -> provider.type().getName())
                .collect(Collectors.toList());
        if (!providers.contains(EXPECTED)) {
            System.err.println("No " + EXPECTED + " bound to McpServerSPI on the module path; providers found: "
                    + providers + ". Check the 'provides' clause of dev.langchain4j.cdi.mcp.server.");
            System.exit(1);
        }
        System.out.println("McpServerSPI on the module path: " + providers);
    }
}
