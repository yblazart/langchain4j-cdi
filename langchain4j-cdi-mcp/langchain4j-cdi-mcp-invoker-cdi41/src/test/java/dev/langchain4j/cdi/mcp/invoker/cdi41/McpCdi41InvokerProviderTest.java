package dev.langchain4j.cdi.mcp.invoker.cdi41;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.registry.McpMethodInvoker;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.invoke.Invoker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link McpCdi41InvokerProvider} and {@link McpCdi41InvokerProviderCreator}: the parallel
 * {@code String[]} / {@code Invoker[]} synthetic-bean parameters must rebuild a lookup table that matches the
 * {@code (beanType, methodName, parameterTypes)} triple {@code McpBeanInvoker} asks with.
 */
class McpCdi41InvokerProviderTest {

    /** Fixture bean standing in for an application bean carrying {@code @Tool} methods. */
    @SuppressWarnings("unused")
    static class Greeter {
        String hello(String name) {
            return "Hello " + name;
        }

        int add(int a, int b) {
            return a + b;
        }
    }

    /** Records what the container-generated invoker was called with. */
    static final class RecordingInvoker implements Invoker<Object, Object> {
        Object instance;
        Object[] args;
        boolean called;
        private final Object result;
        private final Exception failure;

        RecordingInvoker(Object result) {
            this(result, null);
        }

        RecordingInvoker(Object result, Exception failure) {
            this.result = result;
            this.failure = failure;
        }

        @Override
        public Object invoke(Object instance, Object[] args) throws Exception {
            this.called = true;
            this.instance = instance;
            this.args = args;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    /** Minimal {@link Parameters} stand-in; the container supplies the real one at bean creation time. */
    record FakeParameters(Map<String, Object> values) implements Parameters {
        @Override
        public <T> T get(String key, Class<T> type) {
            return type.cast(values.get(key));
        }

        @Override
        public <T> T get(String key, Class<T> type, T defaultValue) {
            Object value = values.get(key);
            return value == null ? defaultValue : type.cast(value);
        }
    }

    private static final String HELLO_KEY = McpInvokerKey.of(Greeter.class, "hello", new Class<?>[] {String.class})
            .encode();
    private static final String ADD_KEY = McpInvokerKey.of(Greeter.class, "add", new Class<?>[] {int.class, int.class})
            .encode();

    @Test
    void lookupReturnsAnInvokerThatDelegatesToTheContainerInvoker() throws Exception {
        RecordingInvoker hello = new RecordingInvoker("Hello world");
        McpCdi41InvokerProvider provider =
                new McpCdi41InvokerProvider(new String[] {HELLO_KEY}, new Invoker<?, ?>[] {hello});

        Optional<McpMethodInvoker> found = provider.lookup(Greeter.class, "hello", new Class<?>[] {String.class});

        assertThat(found).isPresent();
        assertThat(found.get().invoke(null, new Object[] {"world"})).isEqualTo("Hello world");
        assertThat(hello.called).isTrue();
        assertThat(hello.instance).isNull();
        assertThat(hello.args).containsExactly("world");
    }

    @Test
    void invokerReportsThatItResolvesTheBeanInstanceItself() {
        McpCdi41InvokerProvider provider =
                new McpCdi41InvokerProvider(new String[] {HELLO_KEY}, new Invoker<?, ?>[] {new RecordingInvoker("x")});

        assertThat(provider.lookup(Greeter.class, "hello", new Class<?>[] {String.class})
                        .orElseThrow()
                        .resolvesInstance())
                .isTrue();
    }

    @Test
    void invokerPropagatesTheUnderlyingException() {
        IllegalStateException boom = new IllegalStateException("boom");
        McpCdi41InvokerProvider provider = new McpCdi41InvokerProvider(
                new String[] {HELLO_KEY}, new Invoker<?, ?>[] {new RecordingInvoker(null, boom)});

        McpMethodInvoker invoker = provider.lookup(Greeter.class, "hello", new Class<?>[] {String.class})
                .orElseThrow();

        assertThatThrownBy(() -> invoker.invoke(null, new Object[] {"world"})).isSameAs(boom);
    }

    @Test
    void lookupMissReturnsEmpty() {
        McpCdi41InvokerProvider provider =
                new McpCdi41InvokerProvider(new String[] {HELLO_KEY}, new Invoker<?, ?>[] {new RecordingInvoker("x")});

        // unknown method name
        assertThat(provider.lookup(Greeter.class, "goodbye", new Class<?>[] {String.class}))
                .isEmpty();
        // known name, wrong parameter types
        assertThat(provider.lookup(Greeter.class, "hello", new Class<?>[] {int.class}))
                .isEmpty();
        // known signature, different bean class
        assertThat(provider.lookup(String.class, "hello", new Class<?>[] {String.class}))
                .isEmpty();
    }

    @Test
    void emptyOrAbsentParametersYieldAnEmptyProvider() {
        assertThat(new McpCdi41InvokerProvider(null, null).size()).isZero();
        assertThat(new McpCdi41InvokerProvider(new String[0], new Invoker<?, ?>[0]).size())
                .isZero();
        assertThat(new McpCdi41InvokerProvider(null, null).lookup(Greeter.class, "hello", new Class<?>[0]))
                .isEmpty();
    }

    @Test
    void mismatchedParallelArraysAreRejected() {
        assertThatThrownBy(() -> new McpCdi41InvokerProvider(
                        new String[] {HELLO_KEY, ADD_KEY}, new Invoker<?, ?>[] {new RecordingInvoker("x")}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrationOrderDoesNotAffectLookup() {
        List<Optional<String>> resolved = new ArrayList<>();
        for (boolean helloFirst : List.of(true, false)) {
            RecordingInvoker hello = new RecordingInvoker("hello!");
            RecordingInvoker add = new RecordingInvoker(3);
            McpCdi41InvokerProvider provider = helloFirst
                    ? new McpCdi41InvokerProvider(new String[] {HELLO_KEY, ADD_KEY}, new Invoker<?, ?>[] {hello, add})
                    : new McpCdi41InvokerProvider(new String[] {ADD_KEY, HELLO_KEY}, new Invoker<?, ?>[] {add, hello});

            assertThat(provider.size()).isEqualTo(2);
            resolved.add(Optional.ofNullable((String) invokeQuietly(
                    provider.lookup(Greeter.class, "hello", new Class<?>[] {String.class})
                            .orElseThrow(),
                    "world")));
            assertThat(invokeQuietly(
                            provider.lookup(Greeter.class, "add", new Class<?>[] {int.class, int.class})
                                    .orElseThrow(),
                            1,
                            2))
                    .isEqualTo(3);
        }
        assertThat(resolved).containsExactly(Optional.of("hello!"), Optional.of("hello!"));
    }

    @Test
    void creatorRebuildsTheProviderFromTheSyntheticBeanParameters() {
        RecordingInvoker hello = new RecordingInvoker("hi");
        Map<String, Object> params = new HashMap<>();
        params.put(McpCdi41InvokerProviderCreator.PARAM_INVOKER_KEYS, new String[] {HELLO_KEY});
        params.put(McpCdi41InvokerProviderCreator.PARAM_INVOKERS, new Invoker<?, ?>[] {hello});

        McpCdi41InvokerProvider provider =
                new McpCdi41InvokerProviderCreator().create(null, new FakeParameters(params));

        assertThat(provider.size()).isEqualTo(1);
        assertThat(provider.lookup(Greeter.class, "hello", new Class<?>[] {String.class}))
                .isPresent();
    }

    @Test
    void creatorToleratesMissingParameters() {
        McpCdi41InvokerProvider provider =
                new McpCdi41InvokerProviderCreator().create(null, new FakeParameters(Map.of()));

        assertThat(provider.size()).isZero();
    }

    private static Object invokeQuietly(McpMethodInvoker invoker, Object... args) {
        try {
            return invoker.invoke(null, args);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
