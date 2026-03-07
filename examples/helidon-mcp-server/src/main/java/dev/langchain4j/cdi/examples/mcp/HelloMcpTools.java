package dev.langchain4j.cdi.examples.mcp;

import dev.langchain4j.cdi.mcp.server.McpPrompt;
import dev.langchain4j.cdi.mcp.server.McpPromptArg;
import dev.langchain4j.cdi.mcp.server.McpResource;
import dev.langchain4j.cdi.mcp.server.McpTool;
import dev.langchain4j.cdi.mcp.server.McpToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@SuppressWarnings("unused")
@ApplicationScoped
public class HelloMcpTools {

    @McpTool("Say hello to someone by name")
    public String hello(@McpToolArg("The name of the person to greet") String name) {
        return "Hello, " + name + "!";
    }

    @McpResource(
            uri = "config://server",
            name = "Server Config",
            description = "Current server configuration",
            mimeType = "application/json")
    public String serverConfig() {
        return "{\"name\":\"helidon-mcp-server\",\"version\":\"1.0\"}";
    }

    @McpPrompt("Generate a greeting message for a person")
    public String greetingPrompt(@McpPromptArg("The person's name") String name) {
        return "Write a warm and friendly greeting for " + name + ". Be creative and personal.";
    }
}
