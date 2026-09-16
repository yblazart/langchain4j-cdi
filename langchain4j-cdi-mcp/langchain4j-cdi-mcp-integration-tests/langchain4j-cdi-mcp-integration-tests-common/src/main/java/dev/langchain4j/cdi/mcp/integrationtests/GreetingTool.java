package dev.langchain4j.cdi.mcp.integrationtests;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/** MCP tool that generates greeting messages. */
@ApplicationScoped
public class GreetingTool {

    /** Creates a new instance. */
    public GreetingTool() {}

    /**
     * Greets someone by name with an optional prefix.
     *
     * @param name the person's name
     * @param prefix optional greeting prefix, defaults to "Hello"
     * @return the greeting message
     */
    @Tool(description = "Greet someone by name")
    public String greet(
            @ToolArg(description = "The person's name") String name,
            @ToolArg(description = "Optional greeting prefix", required = false) String prefix) {
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + ", " + name + "!";
        }
        return "Hello, " + name + "!";
    }

    /**
     * Greeting style, a <em>nested</em> type so that {@link #describeSignature} covers a binary name carrying a
     * {@code $}.
     *
     * @param prefix the greeting prefix this style uses
     */
    public record Style(String prefix) {}

    /**
     * Deliberately exotic signature: a primitive, an array, a parameterized type and a nested type.
     *
     * <p>Every other MCP method of the shared IT application takes only {@code java.lang.String} parameters, so the
     * "every method was invoked through a container invoker" assertion of {@code McpQuarkusCdi41InvokerTest} used to
     * prove nothing about the type shapes a real container spells differently from {@link Class#getName()} — an array
     * as {@code [Ljava.lang.String;}, a nested class as {@code Outer.Nested}, a generic type unerased. A disagreement
     * on any of those makes this method fall back to reflection, which that test now detects.
     *
     * @param count a primitive parameter
     * @param tags an array parameter
     * @param labels a parameterized-type parameter
     * @param style a nested-type parameter
     * @return a description of the arguments actually received
     */
    @Tool(description = "Echo back a signature mixing a primitive, an array, a generic type and a nested type")
    public String describeSignature(
            @ToolArg(description = "How many greetings", required = false) int count,
            @ToolArg(description = "Extra tags", required = false) String[] tags,
            @ToolArg(description = "Extra labels", required = false) List<String> labels,
            @ToolArg(description = "Greeting style", required = false) Style style) {
        return "count=" + count
                + " tags=" + (tags == null ? "none" : String.join("/", tags))
                + " labels=" + (labels == null ? "none" : String.join("/", labels))
                + " style=" + (style == null ? "none" : style.prefix());
    }
}
