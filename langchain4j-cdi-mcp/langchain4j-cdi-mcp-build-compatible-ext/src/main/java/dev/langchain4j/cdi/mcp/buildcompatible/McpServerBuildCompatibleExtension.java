package dev.langchain4j.cdi.mcp.buildcompatible;

import dev.langchain4j.cdi.mcp.server.McpPrompt;
import dev.langchain4j.cdi.mcp.server.McpResource;
import dev.langchain4j.cdi.mcp.server.McpResourceTemplate;
import dev.langchain4j.cdi.mcp.server.McpTool;
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
    private static final Set<String> detectedResourceBeanClassNames = new HashSet<>();
    private static final Set<String> detectedResourceTemplateBeanClassNames = new HashSet<>();
    private static final Set<String> detectedPromptBeanClassNames = new HashSet<>();

    @SuppressWarnings("unused")
    @Enhancement(types = Object.class, withSubtypes = true)
    public void detectMcpBeans(ClassConfig classConfig) {
        try {
            Class<?> clazz = Thread.currentThread()
                    .getContextClassLoader()
                    .loadClass(classConfig.info().name());

            if (Arrays.stream(clazz.getMethods()).anyMatch(m -> m.isAnnotationPresent(McpTool.class))) {
                LOGGER.info("MCP: Detected @McpTool bean: " + clazz.getName());
                detectedToolBeanClassNames.add(clazz.getName());
            }
            if (Arrays.stream(clazz.getMethods()).anyMatch(m -> m.isAnnotationPresent(McpResource.class))) {
                LOGGER.info("MCP: Detected @McpResource bean: " + clazz.getName());
                detectedResourceBeanClassNames.add(clazz.getName());
            }
            if (Arrays.stream(clazz.getMethods()).anyMatch(m -> m.isAnnotationPresent(McpResourceTemplate.class))) {
                LOGGER.info("MCP: Detected @McpResourceTemplate bean: " + clazz.getName());
                detectedResourceTemplateBeanClassNames.add(clazz.getName());
            }
            if (Arrays.stream(clazz.getMethods()).anyMatch(m -> m.isAnnotationPresent(McpPrompt.class))) {
                LOGGER.info("MCP: Detected @McpPrompt bean: " + clazz.getName());
                detectedPromptBeanClassNames.add(clazz.getName());
            }
        } catch (ClassNotFoundException e) {
            // Ignore classes that can't be loaded
        }
    }

    @SuppressWarnings("unused")
    @Synthesis
    public void registerMcpBeans(SyntheticComponents syntheticComponents) {
        if (detectedToolBeanClassNames.isEmpty()
                && detectedResourceBeanClassNames.isEmpty()
                && detectedResourceTemplateBeanClassNames.isEmpty()
                && detectedPromptBeanClassNames.isEmpty()) {
            LOGGER.info("MCP: No MCP beans detected during build");
            return;
        }

        LOGGER.info("MCP: Synthesizing McpRegistryPopulator with " + detectedToolBeanClassNames.size()
                + " tool(s), " + detectedResourceBeanClassNames.size() + " resource(s), "
                + detectedPromptBeanClassNames.size() + " prompt(s)");

        syntheticComponents
                .addBean(McpToolRegistryPopulator.class)
                .type(McpToolRegistryPopulator.class)
                .scope(ApplicationScoped.class)
                .createWith(McpToolRegistryPopulatorCreator.class)
                .withParam(
                        McpToolRegistryPopulatorCreator.PARAM_TOOL_BEAN_CLASSES,
                        detectedToolBeanClassNames.toArray(new String[0]))
                .withParam(
                        McpToolRegistryPopulatorCreator.PARAM_RESOURCE_BEAN_CLASSES,
                        detectedResourceBeanClassNames.toArray(new String[0]))
                .withParam(
                        McpToolRegistryPopulatorCreator.PARAM_RESOURCE_TEMPLATE_BEAN_CLASSES,
                        detectedResourceTemplateBeanClassNames.toArray(new String[0]))
                .withParam(
                        McpToolRegistryPopulatorCreator.PARAM_PROMPT_BEAN_CLASSES,
                        detectedPromptBeanClassNames.toArray(new String[0]));
    }
}
