package dev.langchain4j.cdi.mcp.server.registry;

/**
 * A container-agnostic strategy for invoking a single CDI bean method, letting the MCP server call
 * {@code @Tool}/{@code @Prompt}/{@code @Resource} methods without reflective {@link java.lang.reflect.Method#invoke}.
 *
 * <p>Implementations are supplied by an {@link McpInvokerProvider} and are typically backed by a container-specific
 * invocation mechanism — for example the Jakarta EE 11 / CDI 4.1 {@code jakarta.enterprise.invoke.Invoker} API. This
 * module stays on CDI 4.0.1 and never references such container-specific types directly; {@link McpBeanInvoker}
 * consults any available provider and falls back to reflection when none supplies an invoker for a given method.
 */
public interface McpMethodInvoker {

    /**
     * Invokes the underlying method.
     *
     * @param beanInstance the resolved bean instance to invoke the method on, or {@code null} when
     *     {@link #resolvesInstance()} is {@code true} and this invoker resolves the instance itself
     * @param args the method arguments, already converted to the method's declared parameter types
     * @return the method's return value
     * @throws Exception any exception raised by the underlying invocation mechanism, including checked or unchecked
     *     exceptions thrown by the target method itself
     */
    Object invoke(Object beanInstance, Object[] args) throws Exception;

    /**
     * Whether this invoker resolves the target bean instance itself, so callers must not resolve one and must pass
     * {@code null} as {@code beanInstance} to {@link #invoke(Object, Object[])}.
     *
     * <p>Defaults to {@code false}. A CDI 4.1 invoker built with {@code InvokerBuilder.withInstanceLookup()} returns
     * {@code true}: the container resolves the bean at invocation time, so {@link McpBeanInvoker} skips its own CDI
     * lookup for methods handled by such an invoker.
     *
     * @return {@code true} when this invoker resolves its own bean instance, {@code false} when the caller must supply
     *     one
     */
    default boolean resolvesInstance() {
        return false;
    }
}
