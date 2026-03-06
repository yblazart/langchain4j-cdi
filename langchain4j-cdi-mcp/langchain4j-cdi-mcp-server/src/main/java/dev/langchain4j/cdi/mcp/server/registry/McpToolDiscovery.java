package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.agent.tool.Tool;
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
    McpToolRegistry registry;

    @Inject
    BeanManager beanManager;

    void onStartup(@Observes @Initialized(ApplicationScoped.class) Object event) {
        if (registry.size() > 0) {
            LOGGER.info("MCP: Tool registry already populated (" + registry.size() + " tools), skipping discovery");
            return;
        }
        discoverToolsFromBeanManager();
    }

    private void discoverToolsFromBeanManager() {
        Set<Bean<?>> allBeans = new HashSet<>();
        allBeans.addAll(beanManager.getBeans(Object.class, Any.Literal.INSTANCE));
        allBeans.addAll(beanManager.getBeans(Object.class));

        for (Bean<?> bean : allBeans) {
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass == null) {
                continue;
            }
            Arrays.stream(beanClass.getMethods())
                    .filter(m -> m.isAnnotationPresent(Tool.class))
                    .forEach(method -> {
                        McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(beanClass, method);
                        registry.register(descriptor);
                        LOGGER.info("MCP: Registered tool '" + descriptor.getName() + "' from "
                                + beanClass.getSimpleName());
                    });
        }

        if (registry.size() > 0) {
            LOGGER.info("MCP: Discovered " + registry.size() + " tool(s) via BeanManager scan");
        }
    }
}
