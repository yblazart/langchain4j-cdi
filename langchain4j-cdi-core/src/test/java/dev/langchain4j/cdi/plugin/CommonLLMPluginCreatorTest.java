package dev.langchain4j.cdi.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.cdi.core.config.TextBlockLLMConfig;
import dev.langchain4j.cdi.core.config.spi.LLMConfigProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommonLLMPluginCreatorTest {

    @SuppressWarnings("unchecked")
    public static final Instance<Object> LOOKUP_MOCKED = mock(Instance.class);

    @SuppressWarnings("unchecked")
    public static final Instance<DummyInjected> DUMMY_INJECTED_INSTANCE_MOCKED = mock(Instance.class);

    public static final Instance<DummyAll.ToInjectAll> DUMMYALL_TOINJECTALL_INSTANCE_MOCKED = mock(Instance.class);
    public static final Instance<DummyAll.ToInjectAllParameterized> DUMMYALL_TOINJECTALLPARAM_INSTANCE_MOCKED =
            mock(Instance.class);

    private static final TextBlockLLMConfig llmConfig = (TextBlockLLMConfig) LLMConfigProvider.getLlmConfig();
    public static final List<String> BEAN_NAMES_LIST = List.of("beanA", "beanB", "beanC");

    @SuppressWarnings({"unchecked", "rawtypes"})
    @BeforeEach
    void beforeEach() {
        DummyInjected dummyInjectedMocked = mock(DummyInjected.class);
        //
        when(LOOKUP_MOCKED.select(DummyInjected.class)).thenReturn(DUMMY_INJECTED_INSTANCE_MOCKED);
        when(LOOKUP_MOCKED.select(DummyInjected.class, NamedLiteral.of(DummyInjected.class.getName())))
                .thenReturn(DUMMY_INJECTED_INSTANCE_MOCKED);
        //
        when(DUMMY_INJECTED_INSTANCE_MOCKED.isResolvable()).thenReturn(true);
        when(DUMMY_INJECTED_INSTANCE_MOCKED.get()).thenReturn(dummyInjectedMocked);
        // Mock BeanManager resolution for ParameterizedType DummyParam<Integer>
        BeanManager bm = mock(BeanManager.class);
        @SuppressWarnings("unchecked")
        Bean<Object> dummyParamBean = (Bean<Object>) mock(Bean.class);
        CreationalContext<Object> ctx = mock(CreationalContext.class);
        java.util.Set<Bean<?>> set = java.util.Set.of(dummyParamBean);
        when(bm.getBeans(org.mockito.ArgumentMatchers.any(java.lang.reflect.Type.class)))
                .thenReturn(set);
        when(bm.resolve(org.mockito.ArgumentMatchers.anySet())).thenReturn((Bean) dummyParamBean);
        when(bm.createCreationalContext(dummyParamBean)).thenReturn(ctx);
        when(bm.getReference(
                        org.mockito.ArgumentMatchers.eq(dummyParamBean),
                        org.mockito.ArgumentMatchers.any(java.lang.reflect.Type.class),
                        org.mockito.ArgumentMatchers.eq(ctx)))
                .thenReturn(new DummyIntegerParam());
        dev.langchain4j.cdi.core.config.spi.LLMConfig.setBeanManagerSupplier(() -> bm);
        //
        llmConfig.reinitForTest("""
                        dev.langchain4j.plugin.beanA.class=dev.langchain4j.cdi.plugin.DummyModel
                        dev.langchain4j.plugin.beanA.scope=jakarta.enterprise.context.RequestScoped
                        dev.langchain4j.plugin.beanA.config.api-key=01
                        dev.langchain4j.plugin.beanA.config.param1=test
                        dev.langchain4j.plugin.beanA.config.timeout=30
                        dev.langchain4j.plugin.beanA.config.dummyEnum=A
                        dev.langchain4j.plugin.beanA.config.dummyEnumList=C,D
                        dev.langchain4j.plugin.beanA.config.dummyInjected=lookup:@default
                        dev.langchain4j.plugin.beanA.config.dummy-param-int=lookup:@default
                        dev.langchain4j.plugin.beanA.config.dummyWithStringConstructor=ok

                        # No scope defined to get ApplicationScoped by default
                        dev.langchain4j.plugin.beanB.class=dev.langchain4j.cdi.plugin.DummyModel
                        dev.langchain4j.plugin.beanB.config.api-key=01
                        dev.langchain4j.plugin.beanB.config.param1=test
                        dev.langchain4j.plugin.beanB.config.timeout=30
                        dev.langchain4j.plugin.beanB.config.dummyEnum=A
                        dev.langchain4j.plugin.beanB.config.dummyEnumList=C,D
                        dev.langchain4j.plugin.beanB.config.dummyInjected=lookup:dev.langchain4j.cdi.plugin.DummyInjected
                        dev.langchain4j.plugin.beanB.config.dummy-param-int=lookup:@default
                        dev.langchain4j.plugin.beanB.config.dummyWithStringConstructor=ok

                        # No scope defined to get ApplicationScoped by default
                        dev.langchain4j.plugin.beanC.class=dev.langchain4j.cdi.plugin.DummyModel
                        dev.langchain4j.plugin.beanC.defined_bean_producer=ProducerC

                        """);

        llmConfig.registerProducer(
                "ProducerC",
                (lookup, beanName, llmCOnfig) -> new DummyModel(
                        "01",
                        30,
                        dummyInjectedMocked,
                        new DummyIntegerParam(),
                        "test",
                        DummyEnum.A,
                        List.of(DummyEnum.C, DummyEnum.D),
                        new DummyWithStringConstructor("ok")));
    }

    private static boolean assertBean(DummyModel dummyModel, String beanName) {
        assertNotNull(dummyModel, "For " + beanName);
        assertNotNull(dummyModel.param1, "For " + beanName + " param1");
        assertNotNull(dummyModel.timeout, "For " + beanName + " timeout");
        assertNotNull(dummyModel.dummyInjected, "For " + beanName + " dummyInjected");
        assertNotNull(dummyModel.dummyEnum, "For " + beanName + " dummyEnum");
        assertNotNull(dummyModel.dummyParamInt, "For " + beanName + " dummyParamInt");
        assertNotNull(dummyModel.dummyEnumList, "For " + beanName + " dummyEnumList");
        assertNotNull(dummyModel.dummyWithStringConstructor, "For " + beanName + " dummyWithStringConstructor");

        assertEquals(DummyEnum.A, dummyModel.dummyEnum, "For " + beanName + " dummyEnum");
        assertEquals(
                "ok",
                dummyModel.dummyWithStringConstructor.getValue(),
                "For " + beanName + " dummyWithStringConstructor");
        assertEquals(List.of(DummyEnum.C, DummyEnum.D), dummyModel.dummyEnumList, "For " + beanName + " dummyEnumList");
        return true;
    }

    @Test
    void createAllBeans() throws ClassNotFoundException {
        List<CommonLLMPluginCreator.BeanData> beanDataList = new ArrayList<>();
        CommonLLMPluginCreator.prepareAllLLMBeans(llmConfig, beanDataList::add);

        assertEquals(
                BEAN_NAMES_LIST.size(),
                beanDataList.size(),
                "Found " + beanDataList.size() + " beans: "
                        + beanDataList.stream()
                                .map(CommonLLMPluginCreator.BeanData::beanName)
                                .toList());
        assertTrue(beanDataList.stream()
                .map(CommonLLMPluginCreator.BeanData::beanName)
                .toList()
                .containsAll(BEAN_NAMES_LIST));
        AtomicReference<CommonLLMPluginCreator.BeanData> beanDataRef = new AtomicReference<>();
        assertTrue(beanDataList.stream()
                .peek(beanDataRef::set)
                .map(bd -> bd.callback().apply(LOOKUP_MOCKED))
                .map(o -> (DummyModel) o)
                .allMatch(dummyModel -> assertBean(dummyModel, beanDataRef.get().beanName())));

        assertTrue(beanDataList.stream()
                .filter(bd -> bd.beanName().equals("beanA"))
                .allMatch(bd -> bd.scopeClass().equals(RequestScoped.class)));
        assertTrue(beanDataList.stream()
                .filter(bd -> !bd.beanName().equals("beanA"))
                .allMatch(bd -> bd.scopeClass().equals(ApplicationScoped.class)));
    }

    @Test
    void prepareAllLLMBeans_noBuilderClass_leadsToNoBeanData() throws ClassNotFoundException {
        llmConfig.reinitForTest("""
                        dev.langchain4j.plugin.noBuilder.class=dev.langchain4j.cdi.plugin.NoBuilderModel
                        """);
        List<CommonLLMPluginCreator.BeanData> list = new ArrayList<>();
        CommonLLMPluginCreator.prepareAllLLMBeans(llmConfig, list::add);
        // The method logs a warning and returns (stops processing this bean). Ensure nothing added.
        assertTrue(
                list.isEmpty(),
                "No BeanData should be created when builder class is missing: "
                        + list.stream()
                                .map(CommonLLMPluginCreator.BeanData::beanName)
                                .toList());
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Test
    void useAllLookup() throws ClassNotFoundException {
        when(LOOKUP_MOCKED.select(DummyAll.ToInjectAll.class)).thenReturn(DUMMYALL_TOINJECTALL_INSTANCE_MOCKED);
        when(LOOKUP_MOCKED.select(DummyAll.ToInjectAll.class, NamedLiteral.of(DummyAll.ToInjectAll.class.getName())))
                .thenReturn(DUMMYALL_TOINJECTALL_INSTANCE_MOCKED);
        List<DummyAll.ToInjectAll> toInjectAllList = new ArrayList<>();
        toInjectAllList.add(new DummyAll.ToInjectAllBeanA());
        toInjectAllList.add(new DummyAll.ToInjectAllBeanB());
        when(DUMMYALL_TOINJECTALL_INSTANCE_MOCKED.isResolvable()).thenReturn(true);
        when(DUMMYALL_TOINJECTALL_INSTANCE_MOCKED.stream()).thenReturn(toInjectAllList.stream());

        BeanManager bm = mock(BeanManager.class);
        @SuppressWarnings("unchecked")
        Bean<Object> beanA = mock(Bean.class);
        Bean<Object> beanB = mock(Bean.class);
        CreationalContext<Object> ctxA = mock(CreationalContext.class);
        CreationalContext<Object> ctxB = mock(CreationalContext.class);
        java.util.Set<Bean<?>> set = java.util.Set.of(beanA, beanB);
        when(bm.getBeans(org.mockito.ArgumentMatchers.any(java.lang.reflect.Type.class)))
                .thenReturn(set);
        when(bm.createCreationalContext(beanA)).thenReturn(ctxA);
        when(bm.createCreationalContext(beanB)).thenReturn(ctxB);
        when(bm.getReference(
                        org.mockito.ArgumentMatchers.eq(beanA),
                        org.mockito.ArgumentMatchers.any(java.lang.reflect.Type.class),
                        org.mockito.ArgumentMatchers.eq(ctxA)))
                .thenReturn(new DummyAll.ToInjectAllParameterizedBeanA());
        when(bm.getReference(
                        org.mockito.ArgumentMatchers.eq(beanB),
                        org.mockito.ArgumentMatchers.any(java.lang.reflect.Type.class),
                        org.mockito.ArgumentMatchers.eq(ctxB)))
                .thenReturn(new DummyAll.ToInjectAllParameterizedBeanB());
        dev.langchain4j.cdi.core.config.spi.LLMConfig.setBeanManagerSupplier(() -> bm);

        llmConfig.reinitForTest("""
                        dev.langchain4j.plugin.beanAll.class=dev.langchain4j.cdi.plugin.DummyAll
                        dev.langchain4j.plugin.beanAll.config.toInjectAll=lookup:@all
                        dev.langchain4j.plugin.beanAll.config.toInjectAllParameterized=lookup:@all
                        """);

        List<CommonLLMPluginCreator.BeanData> beanDataList = new ArrayList<>();
        CommonLLMPluginCreator.prepareAllLLMBeans(llmConfig, beanDataList::add);

        assertEquals(1, beanDataList.size());
        CommonLLMPluginCreator.BeanData beanData = beanDataList.get(0);

        DummyAll object = (DummyAll) beanData.callback().apply(LOOKUP_MOCKED);

        assertTrue(object.toInjectAll.stream()
                .map(Object::getClass)
                .toList()
                .containsAll(List.of(DummyAll.ToInjectAllBeanA.class, DummyAll.ToInjectAllBeanB.class)));
        assertTrue(object.toInjectAllParameterized.stream()
                .map(Object::getClass)
                .toList()
                .containsAll(List.of(
                        DummyAll.ToInjectAllParameterizedBeanA.class, DummyAll.ToInjectAllParameterizedBeanB.class)));
    }

    @Test
    void create_missingField_throwsRuntimeWrappingReflectiveOperation() throws ClassNotFoundException {
        llmConfig.reinitForTest("""
                        dev.langchain4j.plugin.bad.class=dev.langchain4j.cdi.plugin.DummyModel
                        dev.langchain4j.plugin.bad.config.unknown-prop=value
                        """);
        Class<?> target = CommonLLMPluginCreator.loadClass("dev.langchain4j.cdi.plugin.DummyModel");
        Class<?> builder = target.getDeclaredClasses()[0];
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> CommonLLMPluginCreator.create(LOOKUP_MOCKED, llmConfig, "bad", target, builder));
        assertTrue(ex.getCause() instanceof ReflectiveOperationException);
        assertTrue(ex.getMessage().contains("unknown-prop"));
    }

    @Test
    void getFieldsInAllHierarchy_handlesNullAndIncludesParent() throws Exception {
        List<?> empty = CommonLLMPluginCreator.getFieldsInAllHierarchy(null);
        assertNotNull(empty);
        assertTrue(empty.isEmpty());
        // Parent+child aggregation
        Class<?> builder = DummyModel.DummyModelBuilder.class;
        List<java.lang.reflect.Field> fields = CommonLLMPluginCreator.getFieldsInAllHierarchy(builder);
        // Should include fields from DummyBaseModel.Builder: apiKey, timeout, dummyInjected
        assertTrue(fields.stream().anyMatch(f -> f.getName().equals("apiKey")));
        assertTrue(fields.stream().anyMatch(f -> f.getName().equals("timeout")));
        assertTrue(fields.stream().anyMatch(f -> f.getName().equals("dummyInjected")));
        // And from DummyModel.DummyModelBuilder: param1, dummyEnum, dummyEnumList
        assertTrue(fields.stream().anyMatch(f -> f.getName().equals("param1")));
        assertTrue(fields.stream().anyMatch(f -> f.getName().equals("dummyEnum")));
        assertTrue(fields.stream().anyMatch(f -> f.getName().equals("dummyEnumList")));
    }

    @Test
    void loadClass_usesFallbackWhenContextClassLoaderCannotFind() throws Exception {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
                @Override
                public Class<?> loadClass(String name) throws ClassNotFoundException {
                    throw new ClassNotFoundException("forced");
                }
            });
            Class<?> cls = CommonLLMPluginCreator.loadClass("dev.langchain4j.cdi.plugin.DummyModel");
            assertEquals(DummyModel.class, cls);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void dashToCamel_convertsCorrectly() throws Exception {
        assertEquals("apiKey", CommonLLMPluginCreator.dashToCamel("api-key"));
        assertEquals("baseUrl", CommonLLMPluginCreator.dashToCamel("base-url"));
        assertEquals("maxRetries", CommonLLMPluginCreator.dashToCamel("max-retries"));
        assertEquals("timeout", CommonLLMPluginCreator.dashToCamel("timeout"));
        assertEquals("a", CommonLLMPluginCreator.dashToCamel("a"));
        assertEquals("myVeryLongPropertyName", CommonLLMPluginCreator.dashToCamel("my-very-long-property-name"));
    }

    @Test
    void dashToCamel_throwsOnNullOrEmpty() throws Exception {
        try {
            CommonLLMPluginCreator.dashToCamel(null);
            fail("Expected IllegalArgumentException for null");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cannot be null or empty"));
        }
        try {
            CommonLLMPluginCreator.dashToCamel("");
            fail("Expected IllegalArgumentException for empty string");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("cannot be null or empty"));
        }
    }

    @Test
    void findMethodsInAllHierarch_handlesNullAndIncludesParent() throws Exception {
        List<java.lang.reflect.Method> emptyFromNull =
                CommonLLMPluginCreator.findMethodsInAllHierarch(null, "anyMethod");
        assertNotNull(emptyFromNull);
        assertTrue(emptyFromNull.isEmpty());

        // Null method name case
        List<java.lang.reflect.Method> emptyFromNullMethod =
                CommonLLMPluginCreator.findMethodsInAllHierarch(DummyModel.class, null);
        assertNotNull(emptyFromNullMethod);
        assertTrue(emptyFromNullMethod.isEmpty());

        // Empty method name case
        List<java.lang.reflect.Method> emptyFromEmptyMethod =
                CommonLLMPluginCreator.findMethodsInAllHierarch(DummyModel.class, "");
        assertNotNull(emptyFromEmptyMethod);
        assertTrue(emptyFromEmptyMethod.isEmpty());

        // Parent+child aggregation - look for methods in the builder hierarchy
        Class<?> builder = DummyModel.DummyModelBuilder.class;

        // Find a method that exists (like "build")
        List<java.lang.reflect.Method> buildMethods = CommonLLMPluginCreator.findMethodsInAllHierarch(builder, "build");
        assertTrue(!buildMethods.isEmpty(), "Should find at least one 'build' method");

        // Verify we can find methods from parent builder
        List<java.lang.reflect.Method> apiKeyMethods =
                CommonLLMPluginCreator.findMethodsInAllHierarch(builder, "apiKey");
        assertTrue(
                !apiKeyMethods.isEmpty(), "Should find 'apiKey' method from parent builder (DummyBaseModel.Builder)");
    }
}
