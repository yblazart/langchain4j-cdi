package dev.langchain4j.cdi.mcp.server.schema;

import java.lang.reflect.Parameter;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.mcpjava.server.tools.ToolArg;

/**
 * Resolves the wire name of a method parameter from its MCP annotations ({@code @ToolArg}, {@code @PromptArg},
 * {@code @ResourceTemplateArg}), falling back to the Java parameter name.
 */
public final class McpParameterNames {

    /** The sentinel MCP annotations use as their {@code name()} default, meaning "use the element's own name". */
    public static final String DEFAULT_ELEMENT_NAME = "<<element name>>";

    private McpParameterNames() {}

    /**
     * Returns the wire name of a parameter: the {@code name} attribute of its {@code @ToolArg}, {@code @PromptArg} or
     * {@code @ResourceTemplateArg} annotation when set, otherwise the Java parameter name (requires {@code -parameters}
     * compiler flag, or the name is {@code arg0}).
     *
     * @param param the method parameter
     * @return the name the argument is sent under
     */
    public static String resolve(Parameter param) {
        ToolArg toolArg = param.getAnnotation(ToolArg.class);
        if (toolArg != null && !DEFAULT_ELEMENT_NAME.equals(toolArg.name())) {
            return toolArg.name();
        }
        PromptArg promptArg = param.getAnnotation(PromptArg.class);
        if (promptArg != null && !DEFAULT_ELEMENT_NAME.equals(promptArg.name())) {
            return promptArg.name();
        }
        ResourceTemplateArg templateArg = param.getAnnotation(ResourceTemplateArg.class);
        if (templateArg != null && !DEFAULT_ELEMENT_NAME.equals(templateArg.name())) {
            return templateArg.name();
        }
        return param.getName();
    }
}
