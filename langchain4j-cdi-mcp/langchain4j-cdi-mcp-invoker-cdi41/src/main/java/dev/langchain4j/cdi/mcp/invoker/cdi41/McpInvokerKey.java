package dev.langchain4j.cdi.mcp.invoker.cdi41;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Identifies a single bean method across the build-time / runtime boundary of this module.
 *
 * <p>A key is produced twice, from two unrelated representations of the very same method, and the two must be equal:
 *
 * <ul>
 *   <li><strong>at build time</strong>, by {@link McpInvokerBuildCompatibleExtension} from the CDI language model
 *       ({@code ClassInfo.name()} and {@code MethodInfo}), where a method is described by {@code String}s only;
 *   <li><strong>at runtime</strong>, by {@link McpCdi41InvokerProvider} from the {@code (beanType, methodName,
 *       parameterTypes)} triple that {@code dev.langchain4j.cdi.mcp.server.registry.McpBeanInvoker} looks methods up
 *       with, where a method is described by {@link Class} objects.
 * </ul>
 *
 * <p>Both sides therefore agree on one canonical spelling for a type, produced by {@link #typeName(Class)} on the
 * runtime side and by the extension on the build side: the <em>binary</em> name of the class, exactly as
 * {@link Class#getName()} and {@code ClassInfo.name()} return it (so a nested class keeps its {@code $}), a Java
 * keyword for a primitive ({@code int}, {@code boolean}, …), and a trailing {@code []} per array dimension (so
 * {@code java.lang.String[]}, not {@code [Ljava.lang.String;}). Generic types are erased, since the runtime side only
 * ever sees erasures.
 *
 * <p>Because a synthetic bean parameter cannot carry an arbitrary object, keys travel from the extension to the
 * synthetic bean as a {@code String[]} of {@link #encode() encoded} keys, rebuilt with {@link #decode(String)}.
 *
 * <p>Instances are immutable and safe for concurrent use.
 */
public final class McpInvokerKey {

    private static final char BEAN_METHOD_SEPARATOR = '#';
    private static final char PARAMETERS_START = '(';
    private static final char PARAMETERS_END = ')';
    private static final String PARAMETER_SEPARATOR = ",";

    private final String beanClassName;
    private final String methodName;
    private final List<String> parameterTypeNames;

    private McpInvokerKey(String beanClassName, String methodName, List<String> parameterTypeNames) {
        this.beanClassName = Objects.requireNonNull(beanClassName, "beanClassName");
        this.methodName = Objects.requireNonNull(methodName, "methodName");
        this.parameterTypeNames = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(parameterTypeNames, "parameterTypeNames")));
    }

    /**
     * Builds a key from the runtime representation of a method, as handed over by {@code McpBeanInvoker}.
     *
     * @param beanType the CDI bean class declaring the method, never {@code null}
     * @param methodName the method's name, never {@code null}
     * @param parameterTypes the method's parameter types in declaration order; {@code null} is treated as none
     * @return the key for that method, never {@code null}
     */
    public static McpInvokerKey of(Class<?> beanType, String methodName, Class<?>[] parameterTypes) {
        Objects.requireNonNull(beanType, "beanType");
        Class<?>[] types = parameterTypes == null ? new Class<?>[0] : parameterTypes;
        List<String> names = new ArrayList<>(types.length);
        for (Class<?> type : types) {
            names.add(typeName(type));
        }
        return new McpInvokerKey(beanType.getName(), methodName, names);
    }

    /**
     * Builds a key from the build-time representation of a method, where every type is already a canonical name.
     *
     * @param beanClassName the binary name of the CDI bean class declaring the method, never {@code null}
     * @param methodName the method's name, never {@code null}
     * @param parameterTypeNames the canonical names of the method's parameter types in declaration order, never
     *     {@code null}
     * @return the key for that method, never {@code null}
     */
    public static McpInvokerKey of(String beanClassName, String methodName, List<String> parameterTypeNames) {
        return new McpInvokerKey(beanClassName, methodName, parameterTypeNames);
    }

    /**
     * Returns the canonical name this module uses for a runtime type: {@code int}, {@code java.lang.String},
     * {@code java.lang.String[]}, {@code com.acme.Outer$Nested}.
     *
     * @param type the type to name, never {@code null}
     * @return its canonical name, never {@code null}
     */
    public static String typeName(Class<?> type) {
        Objects.requireNonNull(type, "type");
        if (type.isArray()) {
            return typeName(type.getComponentType()) + "[]";
        }
        // Class.getName() already yields "int" for primitives, "void" for void and the binary name
        // (with '$' for nested classes) for every other non-array type.
        return type.getName();
    }

    /**
     * Encodes this key as a single string of the form {@code com.acme.Bean#method(java.lang.String,int)}, so it can be
     * carried in the {@code String[]} parameter of a synthetic bean.
     *
     * <p>The form is unambiguous: a binary class name contains neither {@code #} nor {@code (}, a method name contains
     * no {@code (}, and a canonical type name contains neither {@code ,} nor {@code )}.
     *
     * @return the encoded key, never {@code null}
     */
    public String encode() {
        return beanClassName
                + BEAN_METHOD_SEPARATOR
                + methodName
                + PARAMETERS_START
                + String.join(PARAMETER_SEPARATOR, parameterTypeNames)
                + PARAMETERS_END;
    }

    /**
     * Rebuilds a key from its {@link #encode() encoded} form.
     *
     * @param encoded the encoded key, as produced by {@link #encode()}
     * @return the decoded key, never {@code null}
     * @throws IllegalArgumentException if {@code encoded} is {@code null} or not a well-formed encoded key
     */
    public static McpInvokerKey decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Encoded MCP invoker key must not be null");
        }
        int hash = encoded.indexOf(BEAN_METHOD_SEPARATOR);
        int open = encoded.indexOf(PARAMETERS_START, hash + 1);
        if (hash <= 0 || open <= hash + 1 || encoded.charAt(encoded.length() - 1) != PARAMETERS_END) {
            throw new IllegalArgumentException("Malformed MCP invoker key: " + encoded);
        }
        String beanClassName = encoded.substring(0, hash);
        String methodName = encoded.substring(hash + 1, open);
        String parameters = encoded.substring(open + 1, encoded.length() - 1);
        List<String> parameterTypeNames =
                parameters.isEmpty() ? List.of() : List.of(parameters.split(PARAMETER_SEPARATOR, -1));
        return new McpInvokerKey(beanClassName, methodName, parameterTypeNames);
    }

    /**
     * Returns the binary name of the bean class declaring the method.
     *
     * @return the bean class name, never {@code null}
     */
    public String beanClassName() {
        return beanClassName;
    }

    /**
     * Returns the method's name.
     *
     * @return the method name, never {@code null}
     */
    public String methodName() {
        return methodName;
    }

    /**
     * Returns the canonical names of the method's parameter types, in declaration order.
     *
     * @return an immutable list of parameter type names, never {@code null}
     */
    public List<String> parameterTypeNames() {
        return parameterTypeNames;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof McpInvokerKey that)) {
            return false;
        }
        return beanClassName.equals(that.beanClassName)
                && methodName.equals(that.methodName)
                && parameterTypeNames.equals(that.parameterTypeNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(beanClassName, methodName, parameterTypeNames);
    }

    @Override
    public String toString() {
        return encode();
    }
}
