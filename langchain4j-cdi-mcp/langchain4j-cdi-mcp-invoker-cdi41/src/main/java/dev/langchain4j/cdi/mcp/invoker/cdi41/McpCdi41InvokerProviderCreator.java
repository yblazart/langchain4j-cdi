package dev.langchain4j.cdi.mcp.invoker.cdi41;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.invoke.Invoker;
import java.util.logging.Logger;

/**
 * Creation function of the synthetic {@link McpCdi41InvokerProvider} bean registered by
 * {@link McpInvokerBuildCompatibleExtension}.
 *
 * <p>It reads back the two parallel arrays the extension stored on the synthetic bean: the
 * {@link McpInvokerKey#encode() encoded} method keys and the invokers built for them. The container transforms the
 * {@code InvokerInfo[]} the extension passed into a ready-to-use {@code jakarta.enterprise.invoke.Invoker[]} before
 * handing it over, which is why the value is read as {@code Invoker[].class} and not as {@code InvokerInfo[].class}.
 *
 * <p>Missing parameters are tolerated and yield an empty provider, so the MCP server simply keeps using reflection.
 */
public class McpCdi41InvokerProviderCreator implements SyntheticBeanCreator<McpCdi41InvokerProvider> {

    /** Creates a new instance. */
    public McpCdi41InvokerProviderCreator() {}

    private static final Logger LOGGER = Logger.getLogger(McpCdi41InvokerProviderCreator.class.getName());

    /** Synthetic bean parameter name for the array of {@link McpInvokerKey#encode() encoded} method keys. */
    public static final String PARAM_INVOKER_KEYS = "mcpInvokerKeys";

    /** Synthetic bean parameter name for the array of invokers, parallel to {@link #PARAM_INVOKER_KEYS}. */
    public static final String PARAM_INVOKERS = "mcpInvokers";

    /**
     * Builds the provider from the synthetic bean parameters.
     *
     * @param lookup CDI lookup for the bean being created; unused, the provider has no injected collaborator
     * @param params the synthetic bean parameter map declared by {@link McpInvokerBuildCompatibleExtension}
     * @return the provider, never {@code null}
     */
    @Override
    public McpCdi41InvokerProvider create(Instance<Object> lookup, Parameters params) {
        String[] keys = params.get(PARAM_INVOKER_KEYS, String[].class);
        Invoker<?, ?>[] invokers = params.get(PARAM_INVOKERS, Invoker[].class);
        McpCdi41InvokerProvider provider = new McpCdi41InvokerProvider(keys, invokers);
        LOGGER.info(() -> "MCP: CDI 4.1 invoker provider ready for " + provider.size()
                + " method(s); those methods are invoked without reflection");
        return provider;
    }
}
