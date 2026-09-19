package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.cdi.mcp.server.api.McpApiFactory;
import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpInvalidArgumentException;
import dev.langchain4j.cdi.mcp.server.fixtures.ArgumentBindingTool;
import dev.langchain4j.cdi.mcp.server.fixtures.ArgumentBindingTool.Priority;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * langchain4j-cdi#298 through {@link McpBeanInvoker}, on both invocation paths: reflection, and a
 * {@link McpMethodInvoker} supplied by an {@link McpInvokerProvider} (the CDI 4.1 invoker module).
 */
class McpBeanInvokerArgumentBindingTest {

    McpBeanInvoker invoker;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setup() {
        BeanManager beanManager = mock(BeanManager.class);
        Bean<Object> bean = (Bean<Object>) mock(Bean.class);
        when(beanManager.getBeans(any(Type.class))).thenReturn((Set) Set.of(bean));
        when(beanManager.resolve(anySet())).thenReturn((Bean) bean);
        when(beanManager.createCreationalContext(any())).thenReturn((CreationalContext) mock(CreationalContext.class));
        when(beanManager.getReference(any(), any(), any())).thenReturn(new ArgumentBindingTool());

        invoker = new McpBeanInvoker();
        invoker.beanManager = beanManager;
        invoker.apiFactory = mock(McpApiFactory.class);
        invoker.invokerProviders = noProvider();
    }

    private static Method listTasks() throws Exception {
        return ArgumentBindingTool.class.getMethod(
                "listTasks", Integer.class, int.class, boolean.class, Priority.class);
    }

    private static JsonObject args(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    private Object call(Method method, String arguments) {
        return invoker.invoke(3, ArgumentBindingTool.class, method, args(arguments));
    }

    // --- reflection path ---

    @Test
    void reflectionAppliesTheDefaultsOfAbsentArguments() throws Exception {
        assertThat(call(listTasks(), "{}")).isEqualTo("limit=20, pageSize=10, includeDone=true, priority=MEDIUM");
    }

    @Test
    void reflectionBindsAnEnum() throws Exception {
        Method byPriority = ArgumentBindingTool.class.getMethod("byPriority", Priority.class);

        assertThat(call(byPriority, "{\"priority\":\"HIGH\"}")).isEqualTo("priority=HIGH");
    }

    @Test
    void reflectionRejectsAWronglyTypedArgument() throws Exception {
        assertThatThrownBy(() -> call(listTasks(), "{\"limit\":\"5\"}"))
                .isInstanceOfSatisfying(McpInvalidArgumentException.class, e -> {
                    assertThat(e).hasMessage("Invalid argument 'limit': expected integer, got string \"5\"");
                    assertThat(e.getRequestId()).isEqualTo(3);
                });
        assertThatThrownBy(() -> call(listTasks(), "{\"limit\":5,\"pageSize\":10,\"includeDone\":\"true\"}"))
                .isInstanceOf(McpInvalidArgumentException.class)
                .hasMessage("Invalid argument 'includeDone': expected boolean, got string \"true\"");
    }

    @Test
    void reflectionParsesAPromptArgumentSentAsAString() throws Exception {
        Method planDay = ArgumentBindingTool.class.getMethod("planDay", int.class);

        assertThat(call(planDay, "{}")).isEqualTo("hours=8");
        assertThat(call(planDay, "{\"hours\":\"6\"}")).isEqualTo("hours=6");
    }

    @Test
    void aParameterTypeTheBinderDoesNotConvertIsAnInternalErrorNotAnEscapingException() throws Exception {
        Method tag = ArgumentBindingTool.class.getMethod("tag", List.class);

        // Method.invoke throws IllegalArgumentException("argument type mismatch"), which used to reach the container
        assertThatThrownBy(() -> call(tag, "{\"tags\":[\"a\",\"b\"]}"))
                .isInstanceOfSatisfying(McpException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(McpErrorCode.INTERNAL_ERROR);
                    assertThat(e.getMessage()).isEqualTo("Tool execution failed");
                });
    }

    // --- provider path ---

    @Test
    void theProviderInvokerReceivesTheDefaultsAndTheEnumConstant() throws Exception {
        RecordingInvoker recording = new RecordingInvoker();
        invoker.invokerProviders = providerOf(recording);

        call(listTasks(), "{}");
        assertThat(recording.args).containsExactly(20, 10, true, Priority.MEDIUM);

        call(ArgumentBindingTool.class.getMethod("byPriority", Priority.class), "{\"priority\":\"HIGH\"}");
        assertThat(recording.args).containsExactly(Priority.HIGH);
    }

    @Test
    void theProviderInvokerIsNeverCalledWithAWronglyTypedArgument() throws Exception {
        RecordingInvoker recording = new RecordingInvoker();
        invoker.invokerProviders = providerOf(recording);

        assertThatThrownBy(() -> call(listTasks(), "{\"limit\":\"5\"}"))
                .isInstanceOf(McpInvalidArgumentException.class)
                .hasMessage("Invalid argument 'limit': expected integer, got string \"5\"");
        assertThat(recording.args).isNull();
    }

    @SuppressWarnings("unchecked")
    private static Instance<McpInvokerProvider> noProvider() {
        Instance<McpInvokerProvider> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(true);
        return instance;
    }

    @SuppressWarnings("unchecked")
    private static Instance<McpInvokerProvider> providerOf(McpMethodInvoker methodInvoker) {
        McpInvokerProvider provider = (beanType, methodName, parameterTypes) -> Optional.of(methodInvoker);
        Instance<McpInvokerProvider> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(false);
        when(instance.iterator()).thenAnswer(invocation -> List.of(provider).iterator());
        return instance;
    }

    private static final class RecordingInvoker implements McpMethodInvoker {
        Object[] args;

        @Override
        public Object invoke(Object beanInstance, Object[] args) {
            this.args = args;
            return "recorded";
        }

        @Override
        public boolean resolvesInstance() {
            return true;
        }
    }
}
