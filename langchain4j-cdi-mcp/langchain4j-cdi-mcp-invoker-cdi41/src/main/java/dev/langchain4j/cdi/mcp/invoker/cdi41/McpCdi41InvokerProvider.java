package dev.langchain4j.cdi.mcp.invoker.cdi41;

import dev.langchain4j.cdi.mcp.server.registry.McpInvokerProvider;
import dev.langchain4j.cdi.mcp.server.registry.McpMethodInvoker;
import jakarta.enterprise.invoke.Invoker;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link McpInvokerProvider} backed by the CDI 4.1 {@code jakarta.enterprise.invoke} API.
 *
 * <p>Instances are created by {@link McpCdi41InvokerProviderCreator} as a synthetic {@code @ApplicationScoped} bean,
 * from the two parallel arrays that {@link McpInvokerBuildCompatibleExtension} passed as synthetic bean parameters: the
 * {@link McpInvokerKey#encode() encoded} keys and the container-built {@link Invoker}s, in the same order. The
 * constructor turns them back into a lookup table, so {@code McpBeanInvoker} resolves an invoker with a single hash
 * lookup and never touches {@link java.lang.reflect.Method#invoke}.
 *
 * <p>Every invoker handed out by this provider was built with {@code InvokerBuilder.withInstanceLookup()}, so it
 * reports {@link McpMethodInvoker#resolvesInstance()} as {@code true}: the container resolves the target bean itself at
 * invocation time and callers pass {@code null} as the instance.
 *
 * <p>The lookup table is immutable after construction and the class is safe for concurrent use.
 */
public class McpCdi41InvokerProvider implements McpInvokerProvider {

    private final Map<McpInvokerKey, McpMethodInvoker> invokersByKey;

    /**
     * Rebuilds the lookup table from the synthetic bean's parallel parameter arrays.
     *
     * @param encodedKeys the {@link McpInvokerKey#encode() encoded} keys; {@code null} or empty yields a provider that
     *     never matches, letting the MCP server fall back to reflection
     * @param invokers the container-built invokers, in the same order as {@code encodedKeys}; {@code null} is treated
     *     as empty
     * @throws IllegalArgumentException if the two arrays have different lengths, or a key is malformed
     */
    public McpCdi41InvokerProvider(String[] encodedKeys, Invoker<?, ?>[] invokers) {
        String[] keys = encodedKeys == null ? new String[0] : encodedKeys;
        Invoker<?, ?>[] values = invokers == null ? new Invoker<?, ?>[0] : invokers;
        if (keys.length != values.length) {
            throw new IllegalArgumentException("MCP invoker keys and invokers must be parallel arrays, got "
                    + keys.length + " key(s) and " + values.length + " invoker(s)");
        }
        Map<McpInvokerKey, McpMethodInvoker> table = new HashMap<>(Math.max(4, keys.length * 2));
        for (int i = 0; i < keys.length; i++) {
            table.put(McpInvokerKey.decode(keys[i]), new Cdi41MethodInvoker(values[i]));
        }
        this.invokersByKey = Map.copyOf(table);
    }

    @Override
    public Optional<McpMethodInvoker> lookup(Class<?> beanType, String methodName, Class<?>[] parameterTypes) {
        if (invokersByKey.isEmpty() || beanType == null || methodName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(invokersByKey.get(McpInvokerKey.of(beanType, methodName, parameterTypes)));
    }

    /**
     * Returns how many methods this provider can invoke without reflection.
     *
     * @return the number of registered invokers, never negative
     */
    public int size() {
        return invokersByKey.size();
    }

    /**
     * Adapts a CDI 4.1 {@link Invoker} to the MCP server's container-agnostic {@link McpMethodInvoker} SPI.
     *
     * <p>{@code Invoker.invoke} and {@code McpMethodInvoker.invoke} share the same contract — both take the instance
     * and the argument array and are declared to throw {@link Exception} — so the adapter only forwards the call and
     * lets {@code McpBeanInvoker} map the failure to a JSON-RPC error.
     */
    private static final class Cdi41MethodInvoker implements McpMethodInvoker {

        private final Invoker<Object, Object> invoker;

        @SuppressWarnings("unchecked")
        private Cdi41MethodInvoker(Invoker<?, ?> invoker) {
            this.invoker = (Invoker<Object, Object>) invoker;
        }

        @Override
        public Object invoke(Object beanInstance, Object[] args) throws Exception {
            return invoker.invoke(beanInstance, args);
        }

        @Override
        public boolean resolvesInstance() {
            // Every invoker built by McpInvokerBuildCompatibleExtension uses withInstanceLookup().
            return true;
        }
    }
}
