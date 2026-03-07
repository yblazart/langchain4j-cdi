package dev.langchain4j.cdi.mcp.integrationtests;

import dev.langchain4j.cdi.mcp.server.McpTool;
import dev.langchain4j.cdi.mcp.server.McpToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GreetingTool {

    @McpTool("Greet someone by name")
    public String greet(
            @McpToolArg("The person's name") String name,
            @McpToolArg(value = "Optional greeting prefix", required = false) String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + ", " + name + "!";
        }
        return "Hello, " + name + "!";
    }
}
