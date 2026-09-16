package dev.langchain4j.cdi.mcp.invoker.cdi41;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link McpInvokerKey}: the key must be produced identically from the build-time form (class name
 * strings coming from the CDI language model) and the runtime form ({@code Class} objects handed over by
 * {@code McpBeanInvoker}), and must survive the {@code String[]} round trip used to carry it through a synthetic bean
 * parameter.
 */
class McpInvokerKeyTest {

    /** Fixture bean whose methods exercise the interesting parameter shapes. */
    @SuppressWarnings("unused")
    static class Fixture {
        static class Nested {}

        void noArgs() {}

        void primitives(int a, boolean b, double c) {}

        void objects(String a, Nested b) {}

        void arrays(String[] a, int[] b, Nested[][] c) {}
    }

    @Test
    void buildTimeAndRuntimeFormsProduceEqualKeys() {
        McpInvokerKey runtime =
                McpInvokerKey.of(Fixture.class, "objects", new Class<?>[] {String.class, Fixture.Nested.class});
        McpInvokerKey buildTime = McpInvokerKey.of(
                Fixture.class.getName(), "objects", List.of("java.lang.String", Fixture.Nested.class.getName()));

        assertThat(runtime).isEqualTo(buildTime);
        assertThat(runtime).hasSameHashCodeAs(buildTime);
        assertThat(runtime.encode()).isEqualTo(buildTime.encode());
    }

    @Test
    void primitiveAndArrayTypesUseTheSameCanonicalNamesOnBothSides() {
        assertThat(McpInvokerKey.typeName(int.class)).isEqualTo("int");
        assertThat(McpInvokerKey.typeName(boolean.class)).isEqualTo("boolean");
        assertThat(McpInvokerKey.typeName(void.class)).isEqualTo("void");
        assertThat(McpInvokerKey.typeName(String.class)).isEqualTo("java.lang.String");
        assertThat(McpInvokerKey.typeName(String[].class)).isEqualTo("java.lang.String[]");
        assertThat(McpInvokerKey.typeName(int[].class)).isEqualTo("int[]");
        assertThat(McpInvokerKey.typeName(Fixture.Nested[][].class)).isEqualTo(Fixture.Nested.class.getName() + "[][]");
        // binary name, i.e. the nested class uses '$' exactly like ClassInfo.name()
        assertThat(McpInvokerKey.typeName(Fixture.Nested.class)).isEqualTo(Fixture.Nested.class.getName());
    }

    @Test
    void keysDifferOnBeanMethodArityAndParameterOrder() {
        McpInvokerKey base = McpInvokerKey.of(Fixture.class, "objects", new Class<?>[] {String.class, Integer.class});

        assertThat(base)
                .isNotEqualTo(McpInvokerKey.of(Fixture.class, "other", new Class<?>[] {String.class, Integer.class}));
        assertThat(base)
                .isNotEqualTo(McpInvokerKey.of(String.class, "objects", new Class<?>[] {String.class, Integer.class}));
        assertThat(base).isNotEqualTo(McpInvokerKey.of(Fixture.class, "objects", new Class<?>[] {String.class}));
        // parameter order is significant: (String, Integer) is not (Integer, String)
        assertThat(base)
                .isNotEqualTo(McpInvokerKey.of(Fixture.class, "objects", new Class<?>[] {Integer.class, String.class}));
    }

    @Test
    void encodeDecodeRoundTripsEveryParameterShape() {
        List<McpInvokerKey> keys = List.of(
                McpInvokerKey.of(Fixture.class, "noArgs", new Class<?>[0]),
                McpInvokerKey.of(Fixture.class, "primitives", new Class<?>[] {int.class, boolean.class, double.class}),
                McpInvokerKey.of(Fixture.class, "objects", new Class<?>[] {String.class, Fixture.Nested.class}),
                McpInvokerKey.of(
                        Fixture.class, "arrays", new Class<?>[] {String[].class, int[].class, Fixture.Nested[][].class
                        }));

        for (McpInvokerKey key : keys) {
            assertThat(McpInvokerKey.decode(key.encode())).isEqualTo(key);
        }
    }

    @Test
    void encodeUsesAStableReadableForm() {
        assertThat(McpInvokerKey.of(Fixture.class, "noArgs", new Class<?>[0]).encode())
                .isEqualTo(Fixture.class.getName() + "#noArgs()");
        assertThat(McpInvokerKey.of(Fixture.class, "primitives", new Class<?>[] {int.class, boolean.class})
                        .encode())
                .isEqualTo(Fixture.class.getName() + "#primitives(int,boolean)");
    }

    @Test
    void decodeRejectsMalformedInput() {
        assertThatThrownBy(() -> McpInvokerKey.decode("no-separators")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpInvokerKey.decode("a.B#m(int")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpInvokerKey.decode(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keysAreUsableAsMapKeysRegardlessOfInsertionOrder() {
        McpInvokerKey a = McpInvokerKey.of(Fixture.class, "noArgs", new Class<?>[0]);
        McpInvokerKey b = McpInvokerKey.of(Fixture.class, "primitives", new Class<?>[] {int.class});

        Map<McpInvokerKey, String> forward = Map.of(a, "a", b, "b");
        Map<McpInvokerKey, String> reverse = Map.of(b, "b", a, "a");

        assertThat(forward).isEqualTo(reverse);
        assertThat(forward.get(McpInvokerKey.decode(a.encode()))).isEqualTo("a");
        assertThat(Optional.ofNullable(forward.get(McpInvokerKey.of(Fixture.class, "absent", new Class<?>[0]))))
                .isEmpty();
    }
}
