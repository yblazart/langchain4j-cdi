package dev.langchain4j.cdi.mcp.server.fixtures;

import dev.langchain4j.cdi.mcp.server.McpTool;
import dev.langchain4j.cdi.mcp.server.McpToolArg;

public class CalculatorTool {

    @McpTool("Add two numbers together")
    public int add(@McpToolArg("First number") int a, @McpToolArg("Second number") int b) {
        return a + b;
    }

    @McpTool("Multiply two numbers")
    public double multiply(@McpToolArg("First number") double a, @McpToolArg("Second number") double b) {
        return a * b;
    }
}
