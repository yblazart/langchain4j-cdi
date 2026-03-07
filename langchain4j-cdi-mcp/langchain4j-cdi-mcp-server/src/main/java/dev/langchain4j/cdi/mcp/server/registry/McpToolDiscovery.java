package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.McpPrompt;
import dev.langchain4j.cdi.mcp.server.McpResource;
import dev.langchain4j.cdi.mcp.server.McpResourceTemplate;
import dev.langchain4j.cdi.mcp.server.McpTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

@ApplicationScoped
public class McpToolDiscovery {

    private static final Logger LOGGER = Logger.getLogger(McpToolDiscovery.class.getName());

    @Inject
    McpToolRegistry toolRegistry;

    @Inject
    McpResourceRegistry resourceRegistry;

    @Inject
    McpPromptRegistry promptRegistry;

    @Inject
    BeanManager beanManager;

    void onStartup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        if (toolRegistry.size() > 0) {
            LOGGER.info("MCP: Tool registry already populated (" + toolRegistry.size() + " tools), skipping discovery");
            return;
        }
        discoverFromBeanManager();
    }

    private void discoverFromBeanManager() {
        Set<Bean<?>> allBeans = new HashSet<>();
        allBeans.addAll(beanManager.getBeans(Object.class, Any.Literal.INSTANCE));
        allBeans.addAll(beanManager.getBeans(Object.class));

        for (Bean<?> bean : allBeans) {
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass == null) {
                continue;
            }
            Arrays.stream(beanClass.getMethods()).forEach(method -> {
                if (method.isAnnotationPresent(McpTool.class)) {
                    McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(beanClass, method);
                    toolRegistry.register(descriptor);
                    LOGGER.info(
                            "MCP: Registered tool '" + descriptor.getName() + "' from " + beanClass.getSimpleName());
                }
                if (method.isAnnotationPresent(McpResource.class)) {
                    McpResourceDescriptor descriptor = McpResourceDescriptor.fromMethod(beanClass, method);
                    resourceRegistry.register(descriptor);
                    LOGGER.info(
                            "MCP: Registered resource '" + descriptor.getUri() + "' from " + beanClass.getSimpleName());
                }
                if (method.isAnnotationPresent(McpResourceTemplate.class)) {
                    McpResourceTemplateDescriptor descriptor =
                            McpResourceTemplateDescriptor.fromMethod(beanClass, method);
                    resourceRegistry.registerTemplate(descriptor);
                    LOGGER.info("MCP: Registered resource template '" + descriptor.getUriTemplate() + "' from "
                            + beanClass.getSimpleName());
                }
                if (method.isAnnotationPresent(McpPrompt.class)) {
                    McpPromptDescriptor descriptor = McpPromptDescriptor.fromMethod(beanClass, method);
                    promptRegistry.register(descriptor);
                    LOGGER.info(
                            "MCP: Registered prompt '" + descriptor.getName() + "' from " + beanClass.getSimpleName());
                }
            });
        }

        int total = toolRegistry.size() + resourceRegistry.size() + promptRegistry.size();
        if (total > 0) {
            LOGGER.info("MCP: Discovered " + toolRegistry.size() + " tool(s), " + resourceRegistry.size()
                    + " resource(s), " + promptRegistry.size() + " prompt(s) via BeanManager scan");
        }
    }
}
