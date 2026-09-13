package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/** Resolves the optional {@code @Named("mcp-server")} {@link McpServerConfig} bean once. */
@ApplicationScoped
public class McpServerConfigResolver {

    @Inject
    @Named("mcp-server")
    Instance<McpServerConfig> configInstance;

    private volatile McpServerConfig cached;

    /** CDI constructor. */
    public McpServerConfigResolver() {}

    /**
     * Creates a resolver around a fixed configuration (tests).
     *
     * @param config the configuration
     */
    public McpServerConfigResolver(McpServerConfig config) {
        this.cached = config;
    }

    /** @return the configuration, or defaults when no bean is defined */
    public McpServerConfig get() {
        McpServerConfig config = cached;
        if (config == null) {
            config = configInstance != null && configInstance.isResolvable()
                    ? configInstance.get()
                    : new McpServerConfig();
            cached = config;
        }
        return config;
    }
}
