package dev.langchain4j.cdi.mcp.invoker.cdi41;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import jakarta.enterprise.lang.model.declarations.ParameterInfo;
import jakarta.enterprise.lang.model.types.ArrayType;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.PrimitiveType;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.TypeVariable;
import jakarta.enterprise.lang.model.types.VoidType;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the <em>build-time half</em> of the key mapping: {@link McpInvokerBuildCompatibleExtension#typeName(Type)} and
 * {@link McpInvokerBuildCompatibleExtension#parameterTypeNames(MethodInfo)}, which turn the CDI language model into the
 * canonical names {@link McpInvokerKey} matches on.
 *
 * <p>This is the one place where a silent, total failure can be introduced: if the build side spelled {@code String[]}
 * as {@code [Ljava.lang.String;} — the way {@link Class#getName()} does for arrays — every lookup would miss,
 * {@code McpBeanInvoker} would fall back to reflection, and every test that only checks "the tool still works" would
 * still pass. So each case is pinned against {@link McpInvokerKey#typeName(Class)}, the runtime half that consumes the
 * {@code Class} objects {@code McpBeanInvoker} actually passes.
 *
 * <p>The language model types are plain interfaces, so they are faked here with {@link Proxy}; {@code ClassInfo} in
 * particular has far too many methods to hand-roll, and only {@code name()} is ever called.
 */
class McpInvokerTypeNameTest {

    /** Fixture whose nested type exercises binary ({@code $}) naming. */
    @SuppressWarnings("unused")
    static class Fixture {
        static class Nested {}
    }

    // ------------------------------------------------------------------------------------------------
    // primitives
    // ------------------------------------------------------------------------------------------------

    static List<Class<?>> allPrimitives() {
        return List.of(
                boolean.class, byte.class, short.class, int.class, long.class, float.class, double.class, char.class);
    }

    @ParameterizedTest
    @MethodSource("allPrimitives")
    void everyPrimitiveKindIsNamedWithItsJavaKeyword(Class<?> primitive) {
        String buildTime = McpInvokerBuildCompatibleExtension.typeName(langModelType(primitive));

        assertThat(buildTime).isEqualTo(McpInvokerKey.typeName(primitive));
        // For a non-array type the canonical name is exactly what McpBeanInvoker's Class carries.
        assertThat(buildTime).isEqualTo(primitive.getName());
    }

    @Test
    void allEightPrimitiveKindsAreCovered() {
        // Guards the exhaustive switch in primitiveName(): a ninth PrimitiveKind would fail to compile there,
        // but a missing *test* case would go unnoticed.
        assertThat(allPrimitives()).hasSize(PrimitiveType.PrimitiveKind.values().length);
    }

    @Test
    void voidIsNamedVoid() {
        assertThat(McpInvokerBuildCompatibleExtension.typeName(langModelType(void.class)))
                .isEqualTo("void")
                .isEqualTo(McpInvokerKey.typeName(void.class));
    }

    // ------------------------------------------------------------------------------------------------
    // classes
    // ------------------------------------------------------------------------------------------------

    @Test
    void classesUseTheBinaryName() {
        assertThat(McpInvokerBuildCompatibleExtension.typeName(langModelType(String.class)))
                .isEqualTo("java.lang.String")
                .isEqualTo(McpInvokerKey.typeName(String.class))
                .isEqualTo(String.class.getName());
    }

    @Test
    void nestedClassesKeepTheDollarOfTheBinaryName() {
        String buildTime = McpInvokerBuildCompatibleExtension.typeName(langModelType(Fixture.Nested.class));

        assertThat(buildTime).contains("$Nested");
        assertThat(buildTime)
                .isEqualTo(McpInvokerKey.typeName(Fixture.Nested.class))
                .isEqualTo(Fixture.Nested.class.getName());
    }

    // ------------------------------------------------------------------------------------------------
    // arrays
    // ------------------------------------------------------------------------------------------------

    @Test
    void oneDimensionalArraysUseTrailingBrackets() {
        String buildTime = McpInvokerBuildCompatibleExtension.typeName(langModelType(String[].class));

        assertThat(buildTime).isEqualTo("java.lang.String[]").isEqualTo(McpInvokerKey.typeName(String[].class));
        // The divergence that makes this test worth having: both halves must agree on the source-like spelling,
        // NOT on the JVM descriptor Class#getName() returns for arrays.
        assertThat(String[].class.getName()).isEqualTo("[Ljava.lang.String;");
        assertThat(buildTime).isNotEqualTo(String[].class.getName());
    }

    @Test
    void primitiveArraysUseTrailingBrackets() {
        assertThat(McpInvokerBuildCompatibleExtension.typeName(langModelType(int[].class)))
                .isEqualTo("int[]")
                .isEqualTo(McpInvokerKey.typeName(int[].class));
    }

    @Test
    void multiDimensionalArraysRepeatTheBrackets() {
        assertThat(McpInvokerBuildCompatibleExtension.typeName(langModelType(Fixture.Nested[][].class)))
                .isEqualTo(Fixture.Nested.class.getName() + "[][]")
                .isEqualTo(McpInvokerKey.typeName(Fixture.Nested[][].class));
    }

    // ------------------------------------------------------------------------------------------------
    // parameterized types and the unsupported kinds
    // ------------------------------------------------------------------------------------------------

    @Test
    void parameterizedTypesAreErasedToTheirRawClass() {
        // List<String> at build time must meet java.util.List at runtime, since that is the erasure
        // McpBeanInvoker's Method#getParameterTypes() yields.
        assertThat(McpInvokerBuildCompatibleExtension.typeName(parameterizedType(List.class)))
                .isEqualTo("java.util.List")
                .isEqualTo(McpInvokerKey.typeName(List.class));
    }

    @Test
    void typeVariablesAndWildcardsAreUnsupportedAndYieldNull() {
        assertThat(McpInvokerBuildCompatibleExtension.typeName(unsupportedType(Type.Kind.TYPE_VARIABLE)))
                .isNull();
        assertThat(McpInvokerBuildCompatibleExtension.typeName(unsupportedType(Type.Kind.WILDCARD_TYPE)))
                .isNull();
    }

    // ------------------------------------------------------------------------------------------------
    // the whole signature: the property the module actually rests on
    // ------------------------------------------------------------------------------------------------

    @Test
    void aBuildTimeSignatureProducesTheSameKeyAsTheRuntimeSignature() {
        Class<?>[] runtimeParameters = {String.class, int.class, String[].class, Fixture.Nested.class, boolean[].class};
        MethodInfo method = methodInfo(
                "chat",
                langModelType(String.class),
                langModelType(int.class),
                langModelType(String[].class),
                langModelType(Fixture.Nested.class),
                langModelType(boolean[].class));

        McpInvokerKey buildTime = McpInvokerKey.of(
                Fixture.class.getName(), "chat", McpInvokerBuildCompatibleExtension.parameterTypeNames(method));
        McpInvokerKey runtime = McpInvokerKey.of(Fixture.class, "chat", runtimeParameters);

        assertThat(buildTime).isEqualTo(runtime);
        assertThat(buildTime.encode()).isEqualTo(runtime.encode());
    }

    @Test
    void aParameterizedParameterAlsoMatchesItsErasure() {
        MethodInfo method = methodInfo("withList", parameterizedType(List.class));

        assertThat(McpInvokerKey.of(
                        Fixture.class.getName(),
                        "withList",
                        McpInvokerBuildCompatibleExtension.parameterTypeNames(method)))
                .isEqualTo(McpInvokerKey.of(Fixture.class, "withList", new Class<?>[] {List.class}));
    }

    @Test
    void aMethodWithNoParametersYieldsAnEmptySignature() {
        assertThat(McpInvokerBuildCompatibleExtension.parameterTypeNames(methodInfo("noArgs")))
                .isEmpty();
    }

    @Test
    void anUnsupportedParameterTypeAbandonsTheWholeMethod() {
        // null means "build no invoker for this method"; it must keep using reflection rather than be
        // registered under a half-correct key.
        MethodInfo method =
                methodInfo("generic", langModelType(String.class), unsupportedType(Type.Kind.TYPE_VARIABLE));

        assertThat(McpInvokerBuildCompatibleExtension.parameterTypeNames(method))
                .isNull();
    }

    // ================================================================================================
    // language model fakes
    // ================================================================================================

    private static final Class<?>[] TYPE_INTERFACES = {
        Type.class, VoidType.class, PrimitiveType.class, ClassType.class, ArrayType.class
    };

    private static final Map<Class<?>, PrimitiveType.PrimitiveKind> PRIMITIVE_KINDS = Map.of(
            boolean.class, PrimitiveType.PrimitiveKind.BOOLEAN,
            byte.class, PrimitiveType.PrimitiveKind.BYTE,
            short.class, PrimitiveType.PrimitiveKind.SHORT,
            int.class, PrimitiveType.PrimitiveKind.INT,
            long.class, PrimitiveType.PrimitiveKind.LONG,
            float.class, PrimitiveType.PrimitiveKind.FLOAT,
            double.class, PrimitiveType.PrimitiveKind.DOUBLE,
            char.class, PrimitiveType.PrimitiveKind.CHAR);

    /**
     * Builds the language-model {@link Type} a CDI container would report for the given runtime class.
     *
     * @param runtimeType the class to mirror; may be {@code void}, a primitive, an array or a plain class
     * @return the faked language-model type
     */
    private static Type langModelType(Class<?> runtimeType) {
        return (Type) Proxy.newProxyInstance(
                McpInvokerTypeNameTest.class.getClassLoader(), TYPE_INTERFACES, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "kind":
                            return kindOf(runtimeType);
                        case "asType":
                        case "asVoid":
                        case "asPrimitive":
                        case "asClass":
                        case "asArray":
                            return proxy;
                        case "primitiveKind":
                            return PRIMITIVE_KINDS.get(runtimeType);
                        case "componentType":
                            return langModelType(runtimeType.getComponentType());
                        case "declaration":
                            return classInfo(runtimeType.getName());
                        case "name":
                            return runtimeType.getName();
                        case "toString":
                            return "langModelType(" + runtimeType.getName() + ")";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    /**
     * Builds a {@link ParameterizedType}, e.g. the {@code List<String>} of a tool parameter. Only the erasure is ever
     * read, so the type arguments are not modelled.
     *
     * @param erasure the raw class the parameterized type erases to
     * @return the faked parameterized type
     */
    private static Type parameterizedType(Class<?> erasure) {
        return (Type) Proxy.newProxyInstance(
                McpInvokerTypeNameTest.class.getClassLoader(),
                new Class<?>[] {Type.class, ParameterizedType.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "kind" -> Type.Kind.PARAMETERIZED_TYPE;
                    case "asType", "asParameterizedType" -> proxy;
                    case "declaration" -> classInfo(erasure.getName());
                    case "toString" -> "parameterizedType(" + erasure.getName() + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Builds a type of a kind this module cannot name: a type variable or a wildcard.
     *
     * @param kind the unsupported kind to report
     * @return the faked type
     */
    private static Type unsupportedType(Type.Kind kind) {
        return (Type) Proxy.newProxyInstance(
                McpInvokerTypeNameTest.class.getClassLoader(),
                new Class<?>[] {Type.class, TypeVariable.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "kind" -> kind;
                    case "asType", "asTypeVariable" -> proxy;
                    case "toString" -> "unsupportedType(" + kind + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Builds a {@link ClassInfo} answering only {@code name()}, the single method this module calls on it.
     *
     * @param binaryName the binary name to report
     * @return the faked class info
     */
    private static ClassInfo classInfo(String binaryName) {
        return (ClassInfo) Proxy.newProxyInstance(
                McpInvokerTypeNameTest.class.getClassLoader(),
                new Class<?>[] {ClassInfo.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "name" -> binaryName;
                    case "toString" -> "classInfo(" + binaryName + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Builds a {@link MethodInfo} with the given name and parameter types.
     *
     * @param name the method name
     * @param parameterTypes the parameter types, in declaration order
     * @return the faked method info
     */
    private static MethodInfo methodInfo(String name, Type... parameterTypes) {
        List<ParameterInfo> parameters = new ArrayList<>();
        Arrays.stream(parameterTypes).forEach(type -> parameters.add(parameterInfo(type)));
        return (MethodInfo) Proxy.newProxyInstance(
                McpInvokerTypeNameTest.class.getClassLoader(),
                new Class<?>[] {MethodInfo.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "name" -> name;
                    case "parameters" -> parameters;
                    case "toString" -> "methodInfo(" + name + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ParameterInfo parameterInfo(Type type) {
        return (ParameterInfo) Proxy.newProxyInstance(
                McpInvokerTypeNameTest.class.getClassLoader(),
                new Class<?>[] {ParameterInfo.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "type" -> type;
                    case "toString" -> "parameterInfo(" + type + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Type.Kind kindOf(Class<?> runtimeType) {
        if (runtimeType == void.class) {
            return Type.Kind.VOID;
        }
        if (runtimeType.isPrimitive()) {
            return Type.Kind.PRIMITIVE;
        }
        if (runtimeType.isArray()) {
            return Type.Kind.ARRAY;
        }
        return Type.Kind.CLASS;
    }
}
