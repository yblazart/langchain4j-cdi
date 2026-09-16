package dev.langchain4j.cdi.mcp.invoker.cdi41;

import dev.langchain4j.cdi.mcp.server.registry.McpInvokerProvider;
import dev.langchain4j.cdi.mcp.server.registry.McpMethodInvoker;
import jakarta.enterprise.invoke.Invoker;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

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

    private static final Logger LOGGER = Logger.getLogger(McpCdi41InvokerProvider.class.getName());

    private final Map<McpInvokerKey, McpMethodInvoker> invokersByKey;
    private final LongAdder matchCount = new LongAdder();
    private final LongAdder missCount = new LongAdder();

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
        Map<McpInvokerKey, McpMethodInvoker> table = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            table.put(McpInvokerKey.decode(keys[i]), new Cdi41MethodInvoker(values[i]));
        }
        this.invokersByKey = Map.copyOf(table);
    }

    @Override
    public Optional<McpMethodInvoker> lookup(Class<?> beanType, String methodName, Class<?>[] parameterTypes) {
        if (beanType == null || methodName == null) {
            // Not enough to even compute a key; not counted, since it is not a real lookup.
            return Optional.empty();
        }
        McpInvokerKey key = McpInvokerKey.of(beanType, methodName, parameterTypes);
        McpMethodInvoker invoker = invokersByKey.get(key);
        if (invoker == null) {
            missCount.increment();
            // A total miss is otherwise invisible: McpBeanInvoker just falls back to reflection and
            // everything keeps working, so name the key that failed to match.
            LOGGER.fine(() -> "MCP: No CDI 4.1 invoker for " + key + "; falling back to reflection");
            return Optional.empty();
        }
        matchCount.increment();
        LOGGER.fine(() -> "MCP: Using the CDI 4.1 invoker for " + key);
        return Optional.of(invoker);
    }

    /**
     * Returns how many methods this provider <em>could</em> invoke without reflection, i.e. how many invokers the
     * build-compatible extension registered.
     *
     * <p>This says nothing about whether any of them ever matched a lookup — see {@link #matchCount()} for that.
     *
     * @return the number of registered invokers, never negative
     */
    public int size() {
        return invokersByKey.size();
    }

    /**
     * Diagnostic counter: how many lookups matched a registered invoker, i.e. how many MCP methods are actually invoked
     * without reflection.
     *
     * <p>This is the number that matters. {@link #size()} only counts what was registered at build time; a build-time
     * and a runtime key that disagree would leave {@code size()} high and this counter at zero, with every call
     * silently falling back to reflection. A test proving the invoker path is live should assert {@code matchCount() >
     * 0}.
     *
     * <p>{@code McpBeanInvoker} caches its lookup per {@code Method}, so these counters count distinct methods
     * resolved, not individual tool calls.
     *
     * @return the number of lookups that found an invoker, never negative
     */
    public long matchCount() {
        return matchCount.sum();
    }

    /**
     * Diagnostic counter: how many lookups found no invoker and therefore fell back to reflection.
     *
     * <p>A non-zero value is not by itself a fault — the MCP server also invokes methods this extension never saw — but
     * a zero {@link #matchCount()} together with a non-zero value here means the key mapping is broken.
     *
     * @return the number of lookups that found no invoker, never negative
     */
    public long missCount() {
        return missCount.sum();
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
