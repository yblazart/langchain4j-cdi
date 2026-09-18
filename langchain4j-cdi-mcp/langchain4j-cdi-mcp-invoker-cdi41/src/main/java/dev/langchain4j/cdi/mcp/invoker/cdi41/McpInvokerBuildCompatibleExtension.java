package dev.langchain4j.cdi.mcp.invoker.cdi41;

import dev.langchain4j.cdi.mcp.server.registry.McpInvokerProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.build.compatible.spi.BeanInfo;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.InvokerFactory;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.build.compatible.spi.Registration;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.types.PrimitiveType;
import jakarta.enterprise.lang.model.types.Type;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.tools.Tool;

/**
 * Build-compatible CDI extension that gives the MCP server reflection-free invocation on CDI 4.1 runtimes.
 *
 * <p>Two phases:
 *
 * <ol>
 *   <li>{@link Registration} — called for every bean (every bean type set contains {@code Object}). For each method
 *       carrying {@code @Tool}, {@code @Prompt}, {@code @Resource} or {@code @ResourceTemplate}, the extension asks the
 *       container's {@link InvokerFactory} for an invoker built {@code withInstanceLookup()}, and keeps the resulting
 *       {@link InvokerInfo} under the method's {@link McpInvokerKey}.
 *   <li>{@link Synthesis} — registers {@link McpCdi41InvokerProvider} as a synthetic {@code @ApplicationScoped} bean
 *       exposing the {@link McpInvokerProvider} SPI type, carrying the collected keys and invokers as two parallel
 *       synthetic bean parameters.
 * </ol>
 *
 * <p>At runtime {@code McpBeanInvoker} injects every {@code McpInvokerProvider} bean and consults it before falling
 * back to {@link java.lang.reflect.Method#invoke}; a method for which no invoker could be built simply keeps the
 * reflective path, so a partial failure degrades instead of breaking.
 *
 * <p>Nothing here is active on Jakarta EE 10 / CDI 4.0.1 runtimes: this extension ships in a separate, optional
 * artifact and is only discovered when that artifact is on the classpath of a CDI 4.1 container.
 */
public class McpInvokerBuildCompatibleExtension implements BuildCompatibleExtension {

    /** Creates a new instance. */
    public McpInvokerBuildCompatibleExtension() {}

    private static final Logger LOGGER = Logger.getLogger(McpInvokerBuildCompatibleExtension.class.getName());

    private static final boolean CDI41_AVAILABLE = isCdi41Available();

    /** The MCP method annotations whose methods are worth an invoker. */
    private static final List<Class<? extends Annotation>> MCP_METHOD_ANNOTATIONS =
            List.of(Tool.class, Prompt.class, Resource.class, ResourceTemplate.class);

    private static boolean isCdi41Available() {
        try {
            Class.forName("jakarta.enterprise.invoke.Invoker");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Invokers collected during {@link Registration}, keyed by {@link McpInvokerKey#encode() encoded} key so a method
     * seen twice (a bean with several bean types, or an inherited method) is only registered once, and iteration order
     * is stable so the two arrays handed to the synthetic bean stay parallel.
     *
     * <p>{@code static} for the same reason as in the sibling {@code langchain4j-cdi-mcp-build-compatible-ext}
     * extension: some ahead-of-time containers instantiate the extension class once per phase, so instance state would
     * not survive from {@code @Registration} to {@code @Synthesis}. Synthesis always clears the map, successful or not,
     * so a failed deployment cannot pin {@link InvokerInfo}s — and with them the deployment classloader — in a static
     * field across a dev-mode reload.
     */
    private static final Map<String, InvokerInfo> COLLECTED_INVOKERS =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /**
     * Builds a CDI 4.1 invoker for every MCP-annotated method of the given bean.
     *
     * @param bean the bean being registered
     * @param invokerFactory the container's invoker factory
     */
    @SuppressWarnings("unused")
    @Registration(types = Object.class)
    public void buildInvokers(BeanInfo bean, InvokerFactory invokerFactory) {
        if (!CDI41_AVAILABLE) {
            return;
        }
        // Only managed (class) beans can be invoker targets; @Registration also runs a second time, after
        // synthesis, for synthetic beans - which have no invocable class of their own.
        if (!bean.isClassBean() || bean.isSynthetic()) {
            return;
        }
        ClassInfo beanClass = bean.declaringClass();
        if (beanClass == null) {
            return;
        }
        for (MethodInfo method : beanClass.methods()) {
            if (method.isConstructor() || method.isStatic() || !isMcpMethod(method)) {
                continue;
            }
            List<String> parameterTypeNames = parameterTypeNames(method);
            if (parameterTypeNames == null) {
                LOGGER.warning(() -> "MCP: Skipping invoker for " + beanClass.name() + "." + method.name()
                        + ": unsupported parameter type; it keeps using reflection");
                continue;
            }
            String key = McpInvokerKey.of(beanClass.name(), method.name(), parameterTypeNames)
                    .encode();
            // First seen wins. ClassInfo.methods() may return several MethodInfos sharing a signature (an
            // override and the method it overrides), and they all collapse onto the one key McpBeanInvoker
            // will ask with; the container dispatches virtually anyway, so either invoker is correct.
            if (COLLECTED_INVOKERS.containsKey(key)) {
                continue;
            }
            try {
                InvokerInfo invoker = invokerFactory
                        .createInvoker(bean, method)
                        .withInstanceLookup()
                        .build();
                COLLECTED_INVOKERS.put(key, invoker);
                LOGGER.fine(() -> "MCP: Built CDI 4.1 invoker for " + key);
            } catch (RuntimeException e) {
                // The container refuses invokers for some methods (e.g. private or non-proxyable targets).
                // Leaving the method out simply keeps it on the reflective path - but log the cause with its
                // stack trace, since this is the only trace of a method silently dropping back to reflection.
                LOGGER.log(
                        Level.WARNING,
                        e,
                        () -> "MCP: Could not build an invoker for " + key + "; it keeps using reflection");
            }
        }
    }

    /**
     * Registers the synthetic {@link McpCdi41InvokerProvider} bean holding every collected invoker.
     *
     * @param syntheticComponents the synthetic component registrar
     */
    @SuppressWarnings("unused")
    @Synthesis
    public void registerInvokerProvider(SyntheticComponents syntheticComponents) {
        if (!CDI41_AVAILABLE) {
            LOGGER.info(() -> "MCP: CDI 4.1 invoker API (jakarta.enterprise.invoke.Invoker) not available;"
                    + " the MCP server keeps using reflection");
            return;
        }
        // The clear is in a finally so that an aborted synthesis cannot leave the static map pinning InvokerInfos -
        // and through them the deployment's classloader - across a Quarkus dev-mode reload.
        try {
            String[] keys;
            InvokerInfo[] invokers;
            synchronized (COLLECTED_INVOKERS) {
                if (COLLECTED_INVOKERS.isEmpty()) {
                    LOGGER.info(
                            () -> "MCP: No MCP method invoker could be built; the MCP server keeps using reflection");
                    return;
                }
                keys = COLLECTED_INVOKERS.keySet().toArray(new String[0]);
                invokers = COLLECTED_INVOKERS.values().toArray(new InvokerInfo[0]);
            }

            // "built", not "invoked without reflection": whether these invokers are ever matched at runtime is
            // decided by McpCdi41InvokerProvider.lookup, which logs each hit and miss at FINE and counts both.
            LOGGER.info(() -> "MCP: Registering a CDI 4.1 invoker provider with " + keys.length
                    + " invoker(s) built for MCP method(s)");

            syntheticComponents
                    .addBean(McpCdi41InvokerProvider.class)
                    .type(McpCdi41InvokerProvider.class)
                    .type(McpInvokerProvider.class)
                    .scope(ApplicationScoped.class)
                    .createWith(McpCdi41InvokerProviderCreator.class)
                    .withParam(McpCdi41InvokerProviderCreator.PARAM_INVOKER_KEYS, keys)
                    .withParam(McpCdi41InvokerProviderCreator.PARAM_INVOKERS, invokers);
        } finally {
            COLLECTED_INVOKERS.clear();
        }
    }

    /**
     * Whether the method is exposed over MCP and therefore worth an invoker.
     *
     * @param method the method to test
     * @return {@code true} if it carries one of the MCP method annotations
     */
    private static boolean isMcpMethod(MethodInfo method) {
        for (Class<? extends Annotation> annotation : MCP_METHOD_ANNOTATIONS) {
            if (method.hasAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Maps the method's parameter types to the canonical names {@link McpInvokerKey} matches on.
     *
     * <p>Package-private rather than private so {@code McpInvokerTypeNameTest} can pin it against
     * {@link McpInvokerKey#typeName(Class)}: a disagreement between the two would make every lookup miss silently.
     *
     * @param method the method to describe
     * @return the canonical parameter type names in declaration order, or {@code null} if any parameter has a type this
     *     module cannot name unambiguously (a type variable or a wildcard), in which case no invoker is built
     */
    static List<String> parameterTypeNames(MethodInfo method) {
        List<ParameterInfo> parameters = method.parameters();
        List<String> names = new ArrayList<>(parameters.size());
        for (ParameterInfo parameter : parameters) {
            String name = typeName(parameter.type());
            if (name == null) {
                return null;
            }
            names.add(name);
        }
        return names;
    }

    /**
     * Returns the canonical name of a language-model type, spelled exactly like {@link McpInvokerKey#typeName(Class)}
     * spells the corresponding runtime {@link Class}.
     *
     * <p>Package-private rather than private so it can be tested directly: this is the single point where the
     * build-time vocabulary is translated into the runtime one, and a mismatch here is invisible at runtime.
     *
     * @param type the language-model type
     * @return its canonical name, or {@code null} for a type variable or wildcard, which has no runtime erasure this
     *     module can determine
     */
    static String typeName(Type type) {
        return switch (type.kind()) {
            case VOID -> "void";
            case PRIMITIVE -> primitiveName(type.asPrimitive().primitiveKind());
            case CLASS -> type.asClass().declaration().name();
            case ARRAY -> {
                String component = typeName(type.asArray().componentType());
                yield component == null ? null : component + "[]";
            }
            // The runtime side only ever sees the erasure, so List<String> must be named java.util.List.
            case PARAMETERIZED_TYPE -> type.asParameterizedType().declaration().name();
            case TYPE_VARIABLE, WILDCARD_TYPE -> null;
        };
    }

    /**
     * Returns the Java keyword for a primitive kind, matching {@code int.class.getName()} and friends.
     *
     * @param kind the primitive kind
     * @return the Java keyword naming that primitive
     */
    private static String primitiveName(PrimitiveType.PrimitiveKind kind) {
        return switch (kind) {
            case BOOLEAN -> "boolean";
            case BYTE -> "byte";
            case SHORT -> "short";
            case INT -> "int";
            case LONG -> "long";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case CHAR -> "char";
        };
    }
}
