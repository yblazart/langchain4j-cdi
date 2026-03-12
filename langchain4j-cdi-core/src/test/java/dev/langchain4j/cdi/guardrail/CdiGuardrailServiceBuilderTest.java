package dev.langchain4j.cdi.guardrail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.service.guardrail.GuardrailService;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CdiGuardrailServiceBuilderTest {

    // --- Test guardrail implementations ---

    public static class TestInputGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    public static class TestOutputGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    public static class GuardrailWithoutNoArgConstructor implements InputGuardrail {
        private final String config;

        public GuardrailWithoutNoArgConstructor(String config) {
            this.config = config;
        }

        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    public static class ClassLevelGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    public static class MethodLevelGuardrail implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    // --- Test AI service interfaces ---

    interface NoGuardrailService {
        String chat(String message);
    }

    @InputGuardrails(TestInputGuardrail.class)
    @OutputGuardrails(TestOutputGuardrail.class)
    interface ClassLevelGuardrailService {
        String chat(String message);

        String chat2(String message);
    }

    interface MethodLevelGuardrailService {
        @InputGuardrails(TestInputGuardrail.class)
        @OutputGuardrails(TestOutputGuardrail.class)
        String guarded(String message);

        String unguarded(String message);
    }

    // --- Tests ---

    @Test
    void build_withNoGuardrails_producesEmptyService() {
        var builder = new CdiGuardrailServiceBuilder(NoGuardrailService.class);
        GuardrailService service = builder.build();

        assertInstanceOf(CdiGuardrailService.class, service);
        assertEquals(NoGuardrailService.class, service.aiServiceClass());
        assertFalse(service.hasInputGuardrails(findMethod(NoGuardrailService.class, "chat")));
        assertFalse(service.hasOutputGuardrails(findMethod(NoGuardrailService.class, "chat")));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void build_withClassLevelAnnotations_appliesGuardrailsToAllMethods() {
        try (MockedStatic<CDI> cdiMock = mockStatic(CDI.class)) {
            CDI cdi = mockCdiWithGuardrails(cdiMock);

            var builder = new CdiGuardrailServiceBuilder(ClassLevelGuardrailService.class);
            GuardrailService service = builder.build();

            Method chat = findMethod(ClassLevelGuardrailService.class, "chat");
            Method chat2 = findMethod(ClassLevelGuardrailService.class, "chat2");

            assertTrue(service.hasInputGuardrails(chat));
            assertTrue(service.hasOutputGuardrails(chat));
            assertTrue(service.hasInputGuardrails(chat2));
            assertTrue(service.hasOutputGuardrails(chat2));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void build_withMethodLevelAnnotations_appliesGuardrailsOnlyToAnnotatedMethods() {
        try (MockedStatic<CDI> cdiMock = mockStatic(CDI.class)) {
            CDI cdi = mockCdiWithGuardrails(cdiMock);

            var builder = new CdiGuardrailServiceBuilder(MethodLevelGuardrailService.class);
            GuardrailService service = builder.build();

            Method guarded = findMethod(MethodLevelGuardrailService.class, "guarded");
            Method unguarded = findMethod(MethodLevelGuardrailService.class, "unguarded");

            assertTrue(service.hasInputGuardrails(guarded));
            assertTrue(service.hasOutputGuardrails(guarded));
            assertFalse(service.hasInputGuardrails(unguarded));
            assertFalse(service.hasOutputGuardrails(unguarded));
        }
    }

    @Test
    void build_withProgrammaticGuardrails_appliesGuardrailsToAllMethods() {
        var inputGuardrail = new TestInputGuardrail();
        var outputGuardrail = new TestOutputGuardrail();

        var builder = new CdiGuardrailServiceBuilder(NoGuardrailService.class);
        builder.inputGuardrails(List.of(inputGuardrail));
        builder.outputGuardrails(List.of(outputGuardrail));
        builder.inputGuardrailsConfig(
                dev.langchain4j.guardrail.config.InputGuardrailsConfig.builder().build());
        builder.outputGuardrailsConfig(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.builder()
                .build());
        GuardrailService service = builder.build();

        Method chat = findMethod(NoGuardrailService.class, "chat");
        assertTrue(service.hasInputGuardrails(chat));
        assertTrue(service.hasOutputGuardrails(chat));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void build_withProgrammaticGuardrailClasses_resolvesBeanFromCdi() {
        try (MockedStatic<CDI> cdiMock = mockStatic(CDI.class)) {
            CDI cdi = mockCdiWithGuardrails(cdiMock);

            var builder = new CdiGuardrailServiceBuilder(NoGuardrailService.class);
            builder.inputGuardrailClasses(List.of(TestInputGuardrail.class));
            builder.outputGuardrailClasses(List.of(TestOutputGuardrail.class));
            builder.inputGuardrailsConfig(dev.langchain4j.guardrail.config.InputGuardrailsConfig.builder()
                    .build());
            builder.outputGuardrailsConfig(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.builder()
                    .build());

            GuardrailService service = builder.build();
            Method chat = findMethod(NoGuardrailService.class, "chat");
            assertTrue(service.hasInputGuardrails(chat));
            assertTrue(service.hasOutputGuardrails(chat));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void getGuardrailInstance_resolvesBeanFromCdi() {
        var expected = new TestInputGuardrail();

        try (MockedStatic<CDI> cdiMock = mockStatic(CDI.class)) {
            CDI cdi = mock(CDI.class);
            cdiMock.when(CDI::current).thenReturn(cdi);
            Instance<TestInputGuardrail> instance = mock(Instance.class);
            doReturn(instance).when(cdi).select(TestInputGuardrail.class);
            when(instance.isResolvable()).thenReturn(true);
            when(instance.get()).thenReturn(expected);

            InputGuardrail result = CdiGuardrailServiceBuilder.getGuardrailInstance(TestInputGuardrail.class);
            assertSame(expected, result);
        }
    }

    @Test
    void getGuardrailInstance_fallsBackToConstructorWhenCdiNotAvailable() {
        try (MockedStatic<CDI> cdiMock = mockStatic(CDI.class)) {
            cdiMock.when(CDI::current).thenThrow(new IllegalStateException("No CDI container"));

            InputGuardrail result = CdiGuardrailServiceBuilder.getGuardrailInstance(TestInputGuardrail.class);
            assertNotNull(result);
            assertInstanceOf(TestInputGuardrail.class, result);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void getGuardrailInstance_fallsBackToConstructorWhenBeanNotResolvable() {
        try (MockedStatic<CDI> cdiMock = mockStatic(CDI.class)) {
            CDI cdi = mock(CDI.class);
            Instance<TestInputGuardrail> instance = mock(Instance.class);

            cdiMock.when(CDI::current).thenReturn(cdi);
            doReturn(instance).when(cdi).select(TestInputGuardrail.class);
            when(instance.isResolvable()).thenReturn(false);

            InputGuardrail result = CdiGuardrailServiceBuilder.getGuardrailInstance(TestInputGuardrail.class);
            assertNotNull(result);
            assertInstanceOf(TestInputGuardrail.class, result);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void build_serviceAiServiceClassIsCorrect() {
        try (MockedStatic<CDI> cdiMock = mockStatic(CDI.class)) {
            mockCdiWithGuardrails(cdiMock);

            var builder = new CdiGuardrailServiceBuilder(ClassLevelGuardrailService.class);
            GuardrailService service = builder.build();

            assertEquals(ClassLevelGuardrailService.class, service.aiServiceClass());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void build_noInputGuardrailsReturnSuccessResult() {
        var builder = new CdiGuardrailServiceBuilder(NoGuardrailService.class);
        GuardrailService service = builder.build();

        Method chat = findMethod(NoGuardrailService.class, "chat");
        assertFalse(service.hasInputGuardrails(chat));
        assertFalse(service.hasOutputGuardrails(chat));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void getGuardrailInstance_throwsExceptionWhenNoNoArgConstructor() {
        try (MockedStatic<CDI> cdiMock = mockStatic(CDI.class)) {
            CDI cdi = mock(CDI.class);
            Instance<GuardrailWithoutNoArgConstructor> instance = mock(Instance.class);

            cdiMock.when(CDI::current).thenReturn(cdi);
            doReturn(instance).when(cdi).select(GuardrailWithoutNoArgConstructor.class);
            when(instance.isResolvable()).thenReturn(false);

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> CdiGuardrailServiceBuilder.getGuardrailInstance(GuardrailWithoutNoArgConstructor.class));

            assertTrue(exception.getMessage().contains("Failed to create guardrail instance"));
        }
    }

    @InputGuardrails(ClassLevelGuardrail.class)
    interface ServiceWithBothLevels {
        @InputGuardrails(MethodLevelGuardrail.class)
        String methodWithOverride(String message);

        String methodWithoutOverride(String message);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void build_methodLevelAnnotationOverridesClassLevel() {
        try (MockedStatic<CDI> cdiMock = mockStatic(CDI.class)) {
            CDI cdi = mock(CDI.class);
            cdiMock.when(CDI::current).thenReturn(cdi);

            // Mock ClassLevelGuardrail
            Instance<ClassLevelGuardrail> classInstance = mock(Instance.class);
            doReturn(classInstance).when(cdi).select(ClassLevelGuardrail.class);
            when(classInstance.isResolvable()).thenReturn(true);
            when(classInstance.get()).thenReturn(new ClassLevelGuardrail());

            // Mock MethodLevelGuardrail
            Instance<MethodLevelGuardrail> methodInstance = mock(Instance.class);
            doReturn(methodInstance).when(cdi).select(MethodLevelGuardrail.class);
            when(methodInstance.isResolvable()).thenReturn(true);
            when(methodInstance.get()).thenReturn(new MethodLevelGuardrail());

            var builder = new CdiGuardrailServiceBuilder(ServiceWithBothLevels.class);
            GuardrailService service = builder.build();

            Method methodWithOverride = findMethod(ServiceWithBothLevels.class, "methodWithOverride");
            Method methodWithoutOverride = findMethod(ServiceWithBothLevels.class, "methodWithoutOverride");

            // Method-level annotation should override class-level
            assertTrue(service.hasInputGuardrails(methodWithOverride));
            // Method without override should use class-level
            assertTrue(service.hasInputGuardrails(methodWithoutOverride));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void build_withProgrammaticGuardrailConfig_propagatesMaxRetries() {
        var inputGuardrail = new TestInputGuardrail();
        var outputGuardrail = new TestOutputGuardrail();

        var builder = new CdiGuardrailServiceBuilder(NoGuardrailService.class);
        builder.inputGuardrails(List.of(inputGuardrail));
        builder.outputGuardrails(List.of(outputGuardrail));
        builder.inputGuardrailsConfig(
                dev.langchain4j.guardrail.config.InputGuardrailsConfig.builder().build());
        builder.outputGuardrailsConfig(dev.langchain4j.guardrail.config.OutputGuardrailsConfig.builder()
                .maxRetries(5)
                .build());
        GuardrailService service = builder.build();

        Method chat = findMethod(NoGuardrailService.class, "chat");
        assertTrue(service.hasInputGuardrails(chat));
        assertTrue(service.hasOutputGuardrails(chat));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void build_withAnnotationAndProgrammaticGuardrails_programmaticTakesPrecedence() {
        try (MockedStatic<CDI> cdiMock = mockStatic(CDI.class)) {
            mockCdiWithGuardrails(cdiMock);

            var programmaticInput = new TestInputGuardrail();

            var builder = new CdiGuardrailServiceBuilder(ClassLevelGuardrailService.class);
            builder.inputGuardrails(List.of(programmaticInput));
            builder.inputGuardrailsConfig(dev.langchain4j.guardrail.config.InputGuardrailsConfig.builder()
                    .build());
            GuardrailService service = builder.build();

            Method chat = findMethod(ClassLevelGuardrailService.class, "chat");
            // Programmatic guardrails + config set => programmatic takes precedence
            assertTrue(service.hasInputGuardrails(chat));
        }
    }

    // --- Helpers ---

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CDI mockCdiWithGuardrails(MockedStatic<CDI> cdiMock) {
        CDI cdi = mock(CDI.class);
        cdiMock.when(CDI::current).thenReturn(cdi);

        Instance<TestInputGuardrail> igInstance = mock(Instance.class);
        doReturn(igInstance).when(cdi).select(TestInputGuardrail.class);
        when(igInstance.isResolvable()).thenReturn(true);
        when(igInstance.get()).thenReturn(new TestInputGuardrail());

        Instance<TestOutputGuardrail> ogInstance = mock(Instance.class);
        doReturn(ogInstance).when(cdi).select(TestOutputGuardrail.class);
        when(ogInstance.isResolvable()).thenReturn(true);
        when(ogInstance.get()).thenReturn(new TestOutputGuardrail());

        return cdi;
    }

    private static Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Method " + name + " not found in " + clazz);
    }
}
