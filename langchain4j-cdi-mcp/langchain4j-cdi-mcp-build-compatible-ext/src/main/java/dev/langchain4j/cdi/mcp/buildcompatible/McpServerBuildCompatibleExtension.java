package dev.langchain4j.cdi.mcp.buildcompatible;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.Synthesis;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class McpServerBuildCompatibleExtension implements BuildCompatibleExtension {

    private static final Logger LOGGER = Logger.getLogger(McpServerBuildCompatibleExtension.class.getName());
    private static final Set<String> detectedToolBeanClassNames = new HashSet<>();

    @SuppressWarnings("unused")
    @Enhancement(types = Object.class, withSubtypes = true)
    public void detectToolBeans(ClassConfig classConfig) {
        try {
            Class<?> clazz = Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass(classConfig.info().name());
            boolean hasToolMethods = Arrays.stream(clazz.getMethods()).anyMatch(m -> m.isAnnotationPresent(Tool.class));
            if (hasToolMethods) {
                LOGGER.info("MCP: Detected @Tool bean: " + clazz.getName());
                detectedToolBeanClassNames.add(clazz.getName());
            }
        } catch (ClassNotFoundException e) {
            // Ignore classes that can't be loaded
        }
    }

    @SuppressWarnings("unused")
    @Synthesis
    public void registerMcpBeans(SyntheticComponents syntheticComponents) {
        if (detectedToolBeanClassNames.isEmpty()) {
            LOGGER.info("MCP: No @Tool beans detected during build");
            return;
        }

        LOGGER.info("MCP: Synthesizing McpToolRegistryPopulator with " + detectedToolBeanClassNames.size()
                + " tool bean class(es)");

        syntheticComponents
                .addBean(McpToolRegistryPopulator.class)
                .type(McpToolRegistryPopulator.class)
                .scope(ApplicationScoped.class)
                .createWith(McpToolRegistryPopulatorCreator.class)
                .withParam(
                        McpToolRegistryPopulatorCreator.PARAM_TOOL_BEAN_CLASSES,
                        detectedToolBeanClassNames.toArray(new String[0]));
    }
}
