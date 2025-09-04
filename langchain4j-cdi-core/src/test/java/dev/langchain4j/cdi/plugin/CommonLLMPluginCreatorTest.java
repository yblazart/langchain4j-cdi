package dev.langchain4j.cdi.plugin;

import dev.langchain4j.cdi.core.config.TextBlockLLMConfig;
import dev.langchain4j.cdi.core.config.spi.LLMConfigProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommonLLMPluginCreatorTest {

    @SuppressWarnings("unchecked")
    public static final Instance<Object> LOOKUP_MOCKED = mock(Instance.class);
    @SuppressWarnings("unchecked")
    public static final Instance<DummyInjected> DUMMY_INJECTED_INSTANCE_MOCKED = mock(Instance.class);
    private final static TextBlockLLMConfig llmConfig = (TextBlockLLMConfig) LLMConfigProvider.getLlmConfig();
    public static final List<String> BEAN_NAMES_LIST = List.of("beanA", "beanB", "beanC");

    @BeforeAll
    static void beforeAll() {
        DummyInjected dummyInjectedMocked = mock(DummyInjected.class);
        //
        when(LOOKUP_MOCKED.select(DummyInjected.class)).thenReturn(DUMMY_INJECTED_INSTANCE_MOCKED);
        when(LOOKUP_MOCKED.select(DummyInjected.class, NamedLiteral.of(DummyInjected.class.getName())))
                .thenReturn(DUMMY_INJECTED_INSTANCE_MOCKED);
        //
        when(DUMMY_INJECTED_INSTANCE_MOCKED.get()).thenReturn(dummyInjectedMocked);
        //
        llmConfig.reinitForTest("""
                dev.langchain4j.plugin.beanA.class=dev.langchain4j.cdi.plugin.DummyModel
                dev.langchain4j.plugin.beanA.scope=jakarta.enterprise.context.RequestScoped
                dev.langchain4j.plugin.beanA.config.api-key=01
                dev.langchain4j.plugin.beanA.config.param1=test
                dev.langchain4j.plugin.beanA.config.timeout=30
                dev.langchain4j.plugin.beanA.config.dummyInjected=lookup:@default

                # No scope defined to get ApplicationScoped by default
                dev.langchain4j.plugin.beanB.class=dev.langchain4j.cdi.plugin.DummyModel
                dev.langchain4j.plugin.beanB.config.api-key=01
                dev.langchain4j.plugin.beanB.config.param1=test
                dev.langchain4j.plugin.beanB.config.timeout=30
                dev.langchain4j.plugin.beanB.config.dummyInjected=lookup:dev.langchain4j.cdi.plugin.DummyInjected

                # No scope defined to get ApplicationScoped by default
                dev.langchain4j.plugin.beanC.class=dev.langchain4j.cdi.plugin.DummyModel
                dev.langchain4j.plugin.beanC.defined_bean_producer=ProducerC

                """);
        llmConfig.registerProducer(
                "ProducerC",
                (lookup, beanName, llmCOnfig) -> new DummyModel("01", 30, dummyInjectedMocked, "test"));
    }

    private static boolean assertBean(DummyModel dummyModel, String beanName) {
        assertNotNull(dummyModel, "For " + beanName);
        assertNotNull(dummyModel.param1, "For " + beanName + " param1");
        assertNotNull(dummyModel.timeout, "For " + beanName + " timeout");
        assertNotNull(dummyModel.dummyInjected, "For " + beanName + " dummyInjected");
        return true;
    }

    @Test
    void createAllBeans() throws ClassNotFoundException {
        List<CommonLLMPluginCreator.BeanData> beanDataList = new ArrayList<>();
        CommonLLMPluginCreator.prepareAllLLMBeans(
                llmConfig,
                beanDataList::add);

        assertEquals(BEAN_NAMES_LIST.size(), beanDataList.size());
        assertTrue(
                beanDataList.stream().map(CommonLLMPluginCreator.BeanData::beanName).toList()
                        .containsAll(BEAN_NAMES_LIST));
        AtomicReference<CommonLLMPluginCreator.BeanData> beanDataRef = new AtomicReference<>();
        assertTrue(
                beanDataList.stream()
                        .peek(beanDataRef::set)
                        .map(bd -> bd.callback().apply(LOOKUP_MOCKED))
                        .map(o -> (DummyModel) o)
                        .allMatch(dummyModel -> assertBean(dummyModel, beanDataRef.get().beanName())));

        assertTrue(
                beanDataList.stream().filter(bd -> bd.beanName().equals("beanA"))
                        .allMatch(bd -> bd.scopeClass().equals(RequestScoped.class)));
        assertTrue(
                beanDataList.stream().filter(bd -> !bd.beanName().equals("beanA"))
                        .allMatch(bd -> bd.scopeClass().equals(ApplicationScoped.class)));

    }

}