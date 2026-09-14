package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.cdi.mcp.server.api.McpApiFactory;
import dev.langchain4j.cdi.mcp.server.fixtures.GreetingTool;
import dev.langchain4j.cdi.mcp.server.fixtures.InheritedTools;
import dev.langchain4j.cdi.mcp.server.fixtures.ReflectionTrapTool;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link McpBeanInvoker}'s use of the {@link McpInvokerProvider} SPI: a supplied {@link McpMethodInvoker} is
 * preferred over reflection, its {@code resolvesInstance()} flag controls whether the CDI bean is looked up, the lookup
 * result is cached per (bean type, {@link Method}) pair, and reflection remains the fallback when no provider (or no
 * matching invoker) is available.
 */
class McpBeanInvokerInvokerSpiTest {

    BeanManager beanManager;
    McpBeanInvoker invoker;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setup() {
        beanManager = mock(BeanManager.class);
        Bean<Object> bean = (Bean<Object>) mock(Bean.class);
        CreationalContext<Object> creationalContext = mock(CreationalContext.class);
        Set<Bean<?>> beans = Set.of(bean);
        when(beanManager.getBeans(any(Type.class))).thenReturn((Set) beans);
        when(beanManager.resolve(anySet())).thenReturn((Bean) bean);
        when(beanManager.createCreationalContext(any())).thenReturn((CreationalContext) creationalContext);

        invoker = new McpBeanInvoker();
        invoker.beanManager = beanManager;
        invoker.apiFactory = mock(McpApiFactory.class);
    }

    @Test
    void shouldUseProviderInvokerInsteadOfReflection() throws Exception {
        Method method = ReflectionTrapTool.class.getMethod("trap");
        RecordingInvoker recordingInvoker = new RecordingInvoker("sentinel", false);
        McpInvokerProvider provider = providerReturning(recordingInvoker);
        invoker.invokerProviders = instanceOf(provider);

        Object result = invoker.invoke("req-1", ReflectionTrapTool.class, method, emptyArguments());

        assertThat(result).isEqualTo("sentinel");
        assertThat(recordingInvoker.invoked).isTrue();
        verify(beanManager).getReference(any(), any(), any());
    }

    @Test
    void shouldNotResolveBeanWhenInvokerResolvesInstance() throws Exception {
        Method method = GreetingTool.class.getMethod("greet", String.class, String.class);
        RecordingInvoker recordingInvoker = new RecordingInvoker("sentinel", true);
        McpInvokerProvider provider = providerReturning(recordingInvoker);
        invoker.invokerProviders = instanceOf(provider);

        Object result = invoker.invoke("req-1", GreetingTool.class, method, emptyArguments());

        assertThat(result).isEqualTo("sentinel");
        assertThat(recordingInvoker.invoked).isTrue();
        assertThat(recordingInvoker.receivedInstance).isNull();
        verify(beanManager, never()).getBeans(any());
        verify(beanManager, never()).getReference(any(), any(), any());
    }

    @Test
    void shouldUseReflectionWhenNoProviderIsPresent() throws Exception {
        Method method = GreetingTool.class.getMethod("greet", String.class, String.class);
        invoker.invokerProviders = instanceOf();
        when(beanManager.getReference(any(), any(), any())).thenReturn(new GreetingTool());

        JsonObject arguments = Json.createObjectBuilder().add("name", "World").build();
        Object result = invoker.invoke("req-1", GreetingTool.class, method, arguments);

        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    void shouldFallBackToReflectionWhenProviderReturnsEmpty() throws Exception {
        Method method = GreetingTool.class.getMethod("greet", String.class, String.class);
        McpInvokerProvider provider = mock(McpInvokerProvider.class);
        when(provider.lookup(any(), any(), any())).thenReturn(Optional.empty());
        invoker.invokerProviders = instanceOf(provider);
        when(beanManager.getReference(any(), any(), any())).thenReturn(new GreetingTool());

        JsonObject arguments = Json.createObjectBuilder().add("name", "World").build();
        Object result = invoker.invoke("req-1", GreetingTool.class, method, arguments);

        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    void shouldCacheTheLookupResultPerMethod() throws Exception {
        Method method = ReflectionTrapTool.class.getMethod("trap");
        RecordingInvoker recordingInvoker = new RecordingInvoker("sentinel", false);
        McpInvokerProvider provider = mock(McpInvokerProvider.class);
        when(provider.lookup(any(), any(), any())).thenReturn(Optional.of(recordingInvoker));
        invoker.invokerProviders = instanceOf(provider);

        invoker.invoke("req-1", ReflectionTrapTool.class, method, emptyArguments());
        invoker.invoke("req-2", ReflectionTrapTool.class, method, emptyArguments());

        verify(provider, times(1)).lookup(any(), any(), any());
    }

    @Test
    void shouldNotShareTheCachedInvokerBetweenTwoBeansInheritingTheSameMethod() throws Exception {
        Method fromFirstBean = InheritedTools.FirstTool.class.getMethod("describe");
        Method fromSecondBean = InheritedTools.SecondTool.class.getMethod("describe");
        // getMethods() hands back the declaring Method for an inherited method, so these two are equal and hash
        // alike: a cache keyed on the Method alone cannot tell the two beans apart.
        assertThat(fromFirstBean).isEqualTo(fromSecondBean);

        RecordingInvoker firstInvoker = new RecordingInvoker("first", true);
        RecordingInvoker secondInvoker = new RecordingInvoker("second", true);
        McpInvokerProvider provider = providerPerBeanType(Map.of(
                InheritedTools.FirstTool.class, firstInvoker,
                InheritedTools.SecondTool.class, secondInvoker));
        invoker.invokerProviders = instanceOf(provider);

        Object firstResult = invoker.invoke("req-1", InheritedTools.FirstTool.class, fromFirstBean, emptyArguments());
        Object secondResult =
                invoker.invoke("req-2", InheritedTools.SecondTool.class, fromSecondBean, emptyArguments());

        assertThat(firstResult)
                .as("the first bean must be invoked through its own invoker")
                .isEqualTo("first");
        assertThat(secondResult)
                .as("the second bean must not reuse the invoker built for the first bean")
                .isEqualTo("second");
        assertThat(secondInvoker.invoked)
                .as("the invoker built for the second bean must actually be used")
                .isTrue();
    }

    /** A provider that offers a different invoker per bean type, as a real container's would. */
    private static McpInvokerProvider providerPerBeanType(Map<Class<?>, McpMethodInvoker> invokers) {
        return (beanType, methodName, parameterTypes) -> Optional.ofNullable(invokers.get(beanType));
    }

    private static McpInvokerProvider providerReturning(McpMethodInvoker methodInvoker) {
        McpInvokerProvider provider = mock(McpInvokerProvider.class);
        when(provider.lookup(any(), any(), any())).thenReturn(Optional.of(methodInvoker));
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static Instance<McpInvokerProvider> instanceOf(McpInvokerProvider... providers) {
        Instance<McpInvokerProvider> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(providers.length == 0);
        when(instance.iterator()).thenAnswer(invocation -> List.of(providers).iterator());
        return instance;
    }

    private static JsonObject emptyArguments() {
        return Json.createObjectBuilder().build();
    }

    private static final class RecordingInvoker implements McpMethodInvoker {
        private final Object result;
        private final boolean resolvesInstance;
        boolean invoked;
        Object receivedInstance;

        RecordingInvoker(Object result, boolean resolvesInstance) {
            this.result = result;
            this.resolvesInstance = resolvesInstance;
        }

        @Override
        public Object invoke(Object beanInstance, Object[] args) {
            invoked = true;
            receivedInstance = beanInstance;
            return result;
        }

        @Override
        public boolean resolvesInstance() {
            return resolvesInstance;
        }
    }
}
