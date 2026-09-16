package dev.langchain4j.cdi.mcp.server.fixtures;

import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/** Fixtures for {@code @Tool.title()} / {@code @Tool.annotations()} wiring tests. */
public class AnnotatedTool {

    @Tool(name = "titled_tool", title = "Human Title", description = "A tool with a title and no annotations override")
    public String titledOnly(@ToolArg(description = "input") String input) {
        return input;
    }

    @Tool(
            name = "annotated_tool",
            description = "A tool with every annotation member set away from its default",
            annotations =
                    @Tool.Annotations(
                            title = "Annotated Title",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public String fullyAnnotated(@ToolArg(description = "input") String input) {
        return input;
    }

    @Tool(
            name = "partially_annotated_tool",
            description = "A tool whose annotations differ from defaults on a single member",
            annotations = @Tool.Annotations(readOnlyHint = true))
    public String partiallyAnnotated(@ToolArg(description = "input") String input) {
        return input;
    }

    @Tool(name = "plain_tool", description = "A tool with no title and default annotations")
    public String plain(@ToolArg(description = "input") String input) {
        return input;
    }
}
