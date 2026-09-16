package dev.langchain4j.cdi.mcp.server.fixtures;

import org.mcpjava.server.tools.Tool;

/**
 * Test fixture whose method fails when invoked reflectively, so tests can prove that {@code McpBeanInvoker} used a
 * supplied {@code McpMethodInvoker} instead of {@link java.lang.reflect.Method#invoke}.
 */
public class ReflectionTrapTool {

    @Tool(description = "Fails if reflection is used to invoke it")
    public String trap() {
        throw new IllegalStateException(
                "Method.invoke must not be used when an McpInvokerProvider supplies an invoker");
    }
}
