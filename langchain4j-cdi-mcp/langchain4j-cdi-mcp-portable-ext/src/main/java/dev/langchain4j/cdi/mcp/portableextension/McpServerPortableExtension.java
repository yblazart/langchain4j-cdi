package dev.langchain4j.cdi.mcp.portableextension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.cdi.mcp.server.registry.McpToolDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpToolRegistry;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class McpServerPortableExtension implements Extension {

    private static final Logger LOGGER = Logger.getLogger(McpServerPortableExtension.class.getName());
    private final List<McpToolCandidate> candidates = new ArrayList<>();

    <T> void onProcessManagedBean(@Observes ProcessManagedBean<T> pmb) {
        Class<?> beanClass = pmb.getAnnotatedBeanClass().getJavaClass();
        List<Method> toolMethods = Arrays.stream(beanClass.getMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .toList();

        if (!toolMethods.isEmpty()) {
            LOGGER.info("MCP: Detected @Tool methods in " + beanClass.getName());
            candidates.add(new McpToolCandidate(beanClass, toolMethods));
        }
    }

    void onAfterDeployment(@Observes AfterDeploymentValidation adv) {
        if (candidates.isEmpty()) {
            LOGGER.info("MCP: No @Tool beans detected, MCP server will have no tools");
            return;
        }

        McpToolRegistry registry = jakarta.enterprise
                .inject
                .spi
                .CDI
                .current()
                .select(McpToolRegistry.class)
                .get();

        for (McpToolCandidate candidate : candidates) {
            for (Method method : candidate.methods()) {
                McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(candidate.beanClass(), method);
                registry.register(descriptor);
                LOGGER.info("MCP: Registered tool '" + descriptor.getName() + "' from "
                        + candidate.beanClass().getSimpleName());
            }
        }
        LOGGER.info("MCP: Registered " + registry.size() + " tool(s) total");
    }
}
