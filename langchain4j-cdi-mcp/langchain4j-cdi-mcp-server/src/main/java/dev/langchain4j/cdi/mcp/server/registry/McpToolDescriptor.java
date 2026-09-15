package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.protocol.McpToolModel;
import dev.langchain4j.cdi.mcp.server.schema.JsonSchemaGenerator;
import dev.langchain4j.cdi.mcp.server.schema.McpHeaderArgValidator;
import jakarta.json.JsonObject;
import java.lang.reflect.Method;
import org.mcpjava.server.tools.Tool;

/** Describes a discovered MCP tool, holding its metadata, JSON Schema, and the backing bean method. */
public class McpToolDescriptor {

    private static final String DEFAULT_NAME = "<<element name>>";

    private final String name;
    private final String description;
    private final JsonObject inputSchema;
    private final JsonObject modernInputSchema;
    private final Class<?> beanType;
    private final Method method;

    /**
     * Creates a tool descriptor with the given metadata, using the same input schema in both protocol eras.
     *
     * @param name the tool name
     * @param description a human-readable description of the tool
     * @param inputSchema the JSON Schema describing the tool's input parameters
     * @param beanType the CDI bean class that declares the tool method
     * @param method the reflective method reference to invoke
     */
    public McpToolDescriptor(
            String name, String description, JsonObject inputSchema, Class<?> beanType, Method method) {
        this(name, description, inputSchema, inputSchema, beanType, method);
    }

    /**
     * Creates a tool descriptor carrying one input schema per protocol era.
     *
     * @param name the tool name
     * @param description a human-readable description of the tool
     * @param inputSchema the JSON Schema served to the 2025-03-26 legacy era
     * @param modernInputSchema the JSON Schema served to the 2026-07-28 era, which carries the SEP-2243
     *     {@code x-mcp-header} argument designations
     * @param beanType the CDI bean class that declares the tool method
     * @param method the reflective method reference to invoke
     */
    public McpToolDescriptor(
            String name,
            String description,
            JsonObject inputSchema,
            JsonObject modernInputSchema,
            Class<?> beanType,
            Method method) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.modernInputSchema = modernInputSchema;
        this.beanType = beanType;
        this.method = method;
    }

    /**
     * Creates a descriptor by inspecting a {@link Tool}-annotated method.
     *
     * @param beanClass the CDI bean class that declares the method
     * @param method the method annotated with {@link Tool}
     * @return a new descriptor populated from the annotation and method signature
     * @throws dev.langchain4j.cdi.mcp.server.error.McpHeaderArgDefinitionException if an
     *     {@link dev.langchain4j.cdi.mcp.server.api.McpHeaderArg} designation violates SEP-2243
     */
    public static McpToolDescriptor fromMethod(Class<?> beanClass, Method method) {
        Tool tool = method.getAnnotation(Tool.class);
        String toolName = DEFAULT_NAME.equals(tool.name()) ? method.getName() : tool.name();
        String toolDescription = tool.description();
        McpHeaderArgValidator.validate(toolName, method);
        return new McpToolDescriptor(
                toolName,
                toolDescription,
                JsonSchemaGenerator.fromMethod(method, false),
                JsonSchemaGenerator.fromMethod(method, true),
                beanClass,
                method);
    }

    /**
     * Converts this descriptor to the MCP wire-format tool representation.
     *
     * @return an MCP {@link McpToolModel} suitable for JSON serialization
     */
    public McpToolModel toWireFormat() {
        return toWireFormat(false);
    }

    /**
     * Converts this descriptor to the MCP wire-format tool representation for a given era.
     *
     * @param modernEra {@code true} for MCP 2026-07-28, which carries the SEP-2243 {@code x-mcp-header} designations;
     *     {@code false} for the 2025-03-26 legacy era, which predates SEP-2243
     * @return an MCP {@link McpToolModel} suitable for JSON serialization
     */
    public McpToolModel toWireFormat(boolean modernEra) {
        return new McpToolModel(name, null, description, modernEra ? modernInputSchema : inputSchema, null, null, null);
    }

    /**
     * Returns the tool name.
     *
     * @return the tool name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the human-readable tool description.
     *
     * @return the tool description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the JSON Schema describing the tool's input parameters.
     *
     * @return the input schema as a JSON object
     */
    public JsonObject getInputSchema() {
        return inputSchema;
    }

    /**
     * Returns the JSON Schema served to the MCP 2026-07-28 era, which carries the SEP-2243 {@code x-mcp-header}
     * argument designations.
     *
     * @return the modern-era input schema as a JSON object
     */
    public JsonObject getModernInputSchema() {
        return modernInputSchema;
    }

    /**
     * Returns the CDI bean class that declares the tool method.
     *
     * @return the bean class
     */
    public Class<?> getBeanType() {
        return beanType;
    }

    /**
     * Returns the reflective method reference for invoking the tool.
     *
     * @return the tool method
     */
    public Method getMethod() {
        return method;
    }
}
