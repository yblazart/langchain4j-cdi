package dev.langchain4j.cdi.core.portableextension;

import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;

import dev.langchain4j.cdi.core.config.spi.LLMConfig;
import dev.langchain4j.cdi.core.config.spi.LLMConfigProvider;
import dev.langchain4j.cdi.plugin.CommonLLMPluginCreator;

public class LangChain4JPluginsPortableExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(LangChain4JPluginsPortableExtension.class.getName());
    private LLMConfig llmConfig;

    void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery,
            @SuppressWarnings("unused") BeanManager beanManager)
            throws ClassNotFoundException {
        if (llmConfig == null) {
            llmConfig = LLMConfigProvider.getLlmConfig();
        }

        CommonLLMPluginCreator.createAllLLMBeans(
                llmConfig,
                beanData -> {
                    LOGGER.fine("Add Bean " + beanData.getTargetClass() + " " + beanData.getScopeClass() + " "
                            + beanData.getBeanName());

                    afterBeanDiscovery.addBean()
                            .types(beanData.getTargetClass())
                            .addTypes(beanData.getTargetClass().getInterfaces())
                            .scope(beanData.getScopeClass())
                            .name(beanData.getBeanName())
                            .qualifiers(NamedLiteral.of(beanData.getBeanName()))
                            .produceWith(beanData.getCallback());

                    LOGGER.info("Types: " + beanData.getTargetClass() + ","
                            + Arrays.stream(beanData.getTargetClass().getInterfaces()).map(Class::getName)
                                    .collect(Collectors.joining(",")));

                });
    }

}
