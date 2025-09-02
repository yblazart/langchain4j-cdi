package dev.langchain4j.cdi.plugin;

import dev.langchain4j.cdi.core.config.TextBlockLLMConfig;
import dev.langchain4j.cdi.core.config.spi.LLMConfigProvider;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommonLLMPluginCreatorTest {

    @SuppressWarnings("unchecked")
    public static final Instance<Object> LOOKUP_MOCKED = mock(Instance.class);
    @SuppressWarnings("unchecked")
    public static final Instance<DummyInjected> DUMMY_INJECTED_INSTANCE_MOCKED = mock(Instance.class);
    private final static TextBlockLLMConfig llmConfig = (TextBlockLLMConfig) LLMConfigProvider.getLlmConfig();

    @BeforeAll
    static void beforeAll() {
        DummyInjected dummyInjectedMocked = mock(DummyInjected.class);
        when(LOOKUP_MOCKED.select(DummyInjected.class)).thenReturn(DUMMY_INJECTED_INSTANCE_MOCKED);
        when(DUMMY_INJECTED_INSTANCE_MOCKED.get()).thenReturn(dummyInjectedMocked);
        llmConfig.reinit("""
                dev.langchain4j.plugin.beanA.class=dev.langchain4j.cdi.plugin.DummyModel
                dev.langchain4j.plugin.beanA.scope=jakarta.enterprise.context.ApplicationScoped
                dev.langchain4j.plugin.beanA.config.api-key=01
                dev.langchain4j.plugin.beanA.config.param1=test
                dev.langchain4j.plugin.beanA.config.timeout=30
                dev.langchain4j.plugin.beanA.config.dummyInjected=lookup:default

                dev.langchain4j.plugin.beanB.class=dev.langchain4j.cdi.plugin.DummyModel
                dev.langchain4j.plugin.beanB.scope=jakarta.enterprise.context.ApplicationScoped
                dev.langchain4j.plugin.beanB.config.api-key=01
                dev.langchain4j.plugin.beanB.config.param1=test
                dev.langchain4j.plugin.beanB.config.timeout=30
                dev.langchain4j.plugin.beanB.config.dummyInjected=lookup:default



                """);
    }

    @Test
    void createBeanManually() {
        assertBean("beanA");
        assertBean("beanB");
    }

    private static void assertBean(String beanName) {
        DummyModel beanA = (DummyModel) CommonLLMPluginCreator.create(
                LOOKUP_MOCKED,
                beanName,
                DummyModel.class,
                DummyModel.DummyModelBuilder.class);

        assertNotNull(beanA);
        assertNotNull(beanA.apiKey);
        assertNotNull(beanA.param1);
        assertNotNull(beanA.timeout);
        assertNotNull(beanA.dummyInjected);
    }

    @Test
    void createAllBeans() throws ClassNotFoundException {
        List<CommonLLMPluginCreator.BeanData> beanDataList = new ArrayList<>();
        CommonLLMPluginCreator.createAllLLMBeans(
                llmConfig,
                beanDataList::add);

        assertEquals(2, beanDataList.size());
        assertEquals("beanA", beanDataList.get(0).beanName());
        assertEquals("beanB", beanDataList.get(1).beanName());
        assertBean(beanDataList.get(0).beanName());
        assertBean(beanDataList.get(1).beanName());
    }

}