package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.McpTool;
import dev.langchain4j.cdi.mcp.server.protocol.McpToolWireFormat;
import dev.langchain4j.cdi.mcp.server.schema.JsonSchemaGenerator;
import jakarta.json.JsonObject;
import java.lang.reflect.Method;

public class McpToolDescriptor {

    private final String name;
    private final String description;
    private final JsonObject inputSchema;
    private final Class<?> beanType;
    private final Method method;

    public McpToolDescriptor(
            String name, String description, JsonObject inputSchema, Class<?> beanType, Method method) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.beanType = beanType;
        this.method = method;
    }

    public static McpToolDescriptor fromMethod(Class<?> beanClass, Method method) {
        McpTool tool = method.getAnnotation(McpTool.class);
        String toolName = tool.name().isEmpty() ? method.getName() : tool.name();
        String toolDescription = tool.value();
        return new McpToolDescriptor(
                toolName, toolDescription, JsonSchemaGenerator.fromMethod(method), beanClass, method);
    }

    public McpToolWireFormat toWireFormat() {
        return new McpToolWireFormat(name, description, inputSchema);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonObject getInputSchema() {
        return inputSchema;
    }

    public Class<?> getBeanType() {
        return beanType;
    }

    public Method getMethod() {
        return method;
    }
}
