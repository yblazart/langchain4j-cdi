package dev.langchain4j.cdi.mcp.server.fixtures;

import dev.langchain4j.cdi.mcp.server.api.McpHeader;
import dev.langchain4j.cdi.mcp.server.api.McpInputSchema;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/** Fixtures for {@code @McpInputSchema} registration-rule tests. */
public class SchemaOverrideTool {

    private static final String VALID_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"],"
                    + "\"additionalProperties\":false}";

    @Tool(name = "valid_override", description = "A tool whose required properties all bind to a parameter")
    @McpInputSchema(VALID_SCHEMA)
    public String validOverride(@ToolArg(name = "name") String name) {
        return name;
    }

    @Tool(name = "non_object_override", description = "An override whose root is not a JSON object of type object")
    @McpInputSchema("{\"type\":\"array\"}")
    public String nonObjectOverride(@ToolArg(name = "name") String name) {
        return name;
    }

    @Tool(name = "unbindable_required_override", description = "An override requiring a property with no parameter")
    @McpInputSchema(
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\",\"missingParam\"]}")
    public String unbindableRequiredOverride(@ToolArg(name = "name") String name) {
        return name;
    }

    @Tool(name = "override_with_header", description = "An override combined with @McpHeader on the same method")
    @McpInputSchema("{\"type\":\"object\",\"properties\":{\"token\":{\"type\":\"string\"}}}")
    public String overrideWithHeader(@ToolArg(name = "token") @McpHeader("Token") String token) {
        return token;
    }
}
