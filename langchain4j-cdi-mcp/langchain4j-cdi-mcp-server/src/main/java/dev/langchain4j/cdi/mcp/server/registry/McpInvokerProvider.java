package dev.langchain4j.cdi.mcp.server.registry;

import java.util.Optional;

/**
 * SPI for supplying container-specific {@link McpMethodInvoker}s, discovered by {@link McpBeanInvoker} as CDI beans.
 *
 * <p>{@link McpBeanInvoker} consults every {@code McpInvokerProvider} bean present on the classpath, in an unspecified
 * order, before falling back to reflective {@link java.lang.reflect.Method#invoke}. When no provider bean is present —
 * the default on Jakarta EE 10 / CDI 4.0.1 runtimes such as WildFly and OpenLiberty — or every provider returns
 * {@link Optional#empty()} for a given method, the reflective path is used unchanged.
 *
 * <p>Implementations are expected to be {@code jakarta.enterprise.context.ApplicationScoped} CDI beans, typically
 * registered by an optional add-on module (e.g. one backed by the CDI 4.1 invoker API) rather than by this module
 * itself.
 */
public interface McpInvokerProvider {

    /**
     * Looks up an invoker for the given method, if this provider can supply one.
     *
     * @param beanType the CDI bean class declaring the method
     * @param methodName the method's name
     * @param parameterTypes the method's parameter types, in declaration order
     * @return an invoker for the method, or {@link Optional#empty()} when this provider has none for it
     */
    Optional<McpMethodInvoker> lookup(Class<?> beanType, String methodName, Class<?>[] parameterTypes);
}
