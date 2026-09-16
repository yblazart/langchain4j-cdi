package dev.langchain4j.cdi.mcp.conformance;

import dev.langchain4j.cdi.mcp.server.registry.McpPromptDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpPromptRegistry;
import dev.langchain4j.cdi.mcp.server.registry.McpToolDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpToolRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.lang.reflect.Method;
import java.util.List;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolResponse;

/**
 * Diagnostic hooks that mutate the tool and prompt lists so that {@code notifications/tools/list_changed} and
 * {@code notifications/prompts/list_changed} can be observed on a {@code subscriptions/listen} stream (SEP-2575).
 */
@ApplicationScoped
public class ConformanceChangeTools {

    private static final String DYNAMIC_TOOL = "test_dynamic_tool";
    private static final String DYNAMIC_PROMPT = "test_dynamic_prompt";
    private static final JsonObject EMPTY_SCHEMA = Json.createObjectBuilder()
            .add("type", "object")
            .add("properties", Json.createObjectBuilder())
            .add("required", Json.createArrayBuilder())
            .build();

    @Inject
    McpToolRegistry toolRegistry;

    @Inject
    McpPromptRegistry promptRegistry;

    /** Creates a new instance. */
    public ConformanceChangeTools() {}

    /**
     * Adds or removes a dynamic tool, which makes the registry broadcast a tools-list-changed notification.
     *
     * @return the tool response
     */
    @Tool(name = "test_trigger_tool_change", description = "Adds or removes a tool to trigger tools/list_changed")
    public ToolResponse triggerToolChange() {
        if (toolRegistry.findTool(DYNAMIC_TOOL).isPresent()) {
            toolRegistry.unregister(DYNAMIC_TOOL);
            return ToolResponse.ofText("Removed " + DYNAMIC_TOOL);
        }
        toolRegistry.register(new McpToolDescriptor(
                DYNAMIC_TOOL,
                "A tool that was dynamically added to trigger a list change",
                EMPTY_SCHEMA,
                ConformanceChangeTools.class,
                dynamicToolMethod()));
        return ToolResponse.ofText("Added " + DYNAMIC_TOOL);
    }

    /**
     * Adds or removes a dynamic prompt, which makes the registry broadcast a prompts-list-changed notification.
     *
     * @return the tool response
     */
    @Tool(name = "test_trigger_prompt_change", description = "Adds or removes a prompt to trigger prompts/list_changed")
    public ToolResponse triggerPromptChange() {
        if (promptRegistry.findPrompt(DYNAMIC_PROMPT).isPresent()) {
            promptRegistry.unregister(DYNAMIC_PROMPT);
            return ToolResponse.ofText("Removed " + DYNAMIC_PROMPT);
        }
        promptRegistry.register(new McpPromptDescriptor(
                DYNAMIC_PROMPT,
                "A prompt that was dynamically added to trigger a list change",
                List.of(),
                ConformanceChangeTools.class,
                dynamicPromptMethod()));
        return ToolResponse.ofText("Added " + DYNAMIC_PROMPT);
    }

    /**
     * Body of the dynamically registered tool.
     *
     * @return the tool response
     */
    public ToolResponse dynamicTool() {
        return ToolResponse.ofText("This tool was added dynamically");
    }

    /**
     * Body of the dynamically registered prompt.
     *
     * @return the prompt text
     */
    public String dynamicPrompt() {
        return "This prompt was added dynamically";
    }

    private static Method dynamicToolMethod() {
        return lookup("dynamicTool");
    }

    private static Method dynamicPromptMethod() {
        return lookup("dynamicPrompt");
    }

    private static Method lookup(String name) {
        try {
            return ConformanceChangeTools.class.getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
