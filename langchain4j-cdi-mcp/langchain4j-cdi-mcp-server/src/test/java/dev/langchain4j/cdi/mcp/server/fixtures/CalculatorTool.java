package dev.langchain4j.cdi.mcp.server.fixtures;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class CalculatorTool {

    @Tool("Add two numbers together")
    public int add(@P("First number") int a, @P("Second number") int b) {
        return a + b;
    }

    @Tool("Multiply two numbers")
    public double multiply(@P("First number") double a, @P("Second number") double b) {
        return a * b;
    }
}
