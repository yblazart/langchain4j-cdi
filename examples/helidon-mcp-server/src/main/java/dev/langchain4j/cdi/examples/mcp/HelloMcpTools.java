package dev.langchain4j.cdi.examples.mcp;

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
}
