package dev.langchain4j.cdi.mcp.buildcompatible;

import dev.langchain4j.cdi.mcp.server.McpTool;
import dev.langchain4j.cdi.mcp.server.registry.McpToolDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpToolRegistry;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import java.util.Arrays;
import java.util.logging.Logger;

public class McpToolRegistryPopulatorCreator implements SyntheticBeanCreator<McpToolRegistryPopulator> {

    private static final Logger LOGGER = Logger.getLogger(McpToolRegistryPopulatorCreator.class.getName());
    public static final String PARAM_TOOL_BEAN_CLASSES = "toolBeanClasses";

    @Override
    public McpToolRegistryPopulator create(jakarta.enterprise.inject.Instance<Object> lookup, Parameters params) {
        String[] classNames = params.get(PARAM_TOOL_BEAN_CLASSES, String[].class);
        McpToolRegistry registry = lookup.select(McpToolRegistry.class).get();

        for (String className : classNames) {
            try {
                Class<?> beanClass =
                        Thread.currentThread().getContextClassLoader().loadClass(className);
                Arrays.stream(beanClass.getMethods())
                        .filter(m -> m.isAnnotationPresent(McpTool.class))
                        .forEach(method -> {
                            McpToolDescriptor descriptor = McpToolDescriptor.fromMethod(beanClass, method);
                            registry.register(descriptor);
                            LOGGER.info("MCP: Registered tool '" + descriptor.getName() + "' from "
                                    + beanClass.getSimpleName());
                        });
            } catch (ClassNotFoundException e) {
                LOGGER.warning("MCP: Could not load @McpTool bean class: " + className);
            }
        }
        LOGGER.info("MCP: Registered " + registry.size() + " tool(s) total via build-compatible extension");

        return new McpToolRegistryPopulator();
    }
}
