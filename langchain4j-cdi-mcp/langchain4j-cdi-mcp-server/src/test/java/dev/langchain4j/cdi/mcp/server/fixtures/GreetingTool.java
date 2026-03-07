package dev.langchain4j.cdi.mcp.server.fixtures;

import dev.langchain4j.cdi.mcp.server.McpTool;
import dev.langchain4j.cdi.mcp.server.McpToolArg;

public class GreetingTool {

    @McpTool("Greet someone")
    public String greet(
            @McpToolArg("The person's name") String name,
            @McpToolArg(value = "Optional greeting style", required = false) String style) {
        if (style != null) {
            return style + ", " + name + "!";
        }
        return "Hello, " + name + "!";
    }
}
