package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.api.McpInputSchema;
import dev.langchain4j.cdi.mcp.server.protocol.McpIconModel;
import dev.langchain4j.cdi.mcp.server.protocol.McpToolAnnotationsModel;
import dev.langchain4j.cdi.mcp.server.protocol.McpToolModel;
import dev.langchain4j.cdi.mcp.server.schema.JsonSchemaGenerator;
import dev.langchain4j.cdi.mcp.server.schema.McpArguments;
import dev.langchain4j.cdi.mcp.server.schema.McpHeaderDesignations;
import dev.langchain4j.cdi.mcp.server.schema.McpHeaderValidator;
import dev.langchain4j.cdi.mcp.server.schema.McpInputSchemaValidator;
import dev.langchain4j.cdi.mcp.server.schema.McpParameterNames;
import jakarta.json.JsonObject;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.mcpjava.server.FeatureType;
import org.mcpjava.server.tools.Tool;

/** Describes a discovered MCP tool, holding its metadata, JSON Schema, and the backing bean method. */
public class McpToolDescriptor {

    private static final String DEFAULT_NAME = McpParameterNames.DEFAULT_ELEMENT_NAME;

    private final String name;
    private final String description;
    private final JsonObject inputSchema;
    private final JsonObject modernInputSchema;
    private final Class<?> beanType;
    private final Method method;
    private final List<McpIconModel> icons;
    private final Map<String, String> headerDesignations;
    private final String title;
    private final McpToolAnnotationsModel annotations;

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
        this(name, description, inputSchema, modernInputSchema, beanType, method, null);
    }

    /**
     * Creates a tool descriptor carrying one input schema per protocol era and a resolved icon list.
     *
     * @param name the tool name
     * @param description a human-readable description of the tool
     * @param inputSchema the JSON Schema served to the 2025-03-26 legacy era
     * @param modernInputSchema the JSON Schema served to the 2026-07-28 era, which carries the SEP-2243
     *     {@code x-mcp-header} argument designations
     * @param beanType the CDI bean class that declares the tool method
     * @param method the reflective method reference to invoke
     * @param icons the icons resolved from {@code @Icons} at registration time, or {@code null} when there are none
     */
    public McpToolDescriptor(
            String name,
            String description,
            JsonObject inputSchema,
            JsonObject modernInputSchema,
            Class<?> beanType,
            Method method,
            List<McpIconModel> icons) {
        this(name, description, inputSchema, modernInputSchema, beanType, method, icons, null, null);
    }

    /**
     * Creates a tool descriptor carrying one input schema per protocol era, a resolved icon list, and the resolved
     * {@code @Tool.title()} / {@code @Tool.annotations()} wire values.
     *
     * @param name the tool name
     * @param description a human-readable description of the tool
     * @param inputSchema the JSON Schema served to the 2025-03-26 legacy era
     * @param modernInputSchema the JSON Schema served to the 2026-07-28 era, which carries the SEP-2243
     *     {@code x-mcp-header} argument designations
     * @param beanType the CDI bean class that declares the tool method
     * @param method the reflective method reference to invoke
     * @param icons the icons resolved from {@code @Icons} at registration time, or {@code null} when there are none
     * @param title the resolved {@code @Tool.title()}, or {@code null} when it equals the default ({@code ""})
     * @param annotations the resolved {@code @Tool.annotations()}, or {@code null} when every member equals its default
     */
    public McpToolDescriptor(
            String name,
            String description,
            JsonObject inputSchema,
            JsonObject modernInputSchema,
            Class<?> beanType,
            Method method,
            List<McpIconModel> icons,
            String title,
            McpToolAnnotationsModel annotations) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.modernInputSchema = modernInputSchema;
        this.beanType = beanType;
        this.method = method;
        this.icons = icons;
        this.headerDesignations = McpHeaderDesignations.of(method);
        this.title = title;
        this.annotations = annotations;
    }

    /**
     * Creates a descriptor by inspecting a {@link Tool}-annotated method.
     *
     * @param beanClass the CDI bean class that declares the method
     * @param method the method annotated with {@link Tool}
     * @return a new descriptor populated from the annotation and method signature
     * @throws dev.langchain4j.cdi.mcp.server.error.McpHeaderDefinitionException if an
     *     {@link dev.langchain4j.cdi.mcp.server.api.McpHeader} designation violates SEP-2243
     * @throws dev.langchain4j.cdi.mcp.server.error.McpIconProviderException if an {@code @Icons} annotation names an
     *     icon provider that cannot be resolved
     * @throws dev.langchain4j.cdi.mcp.server.error.McpInputSchemaDefinitionException if an {@link McpInputSchema}
     *     document violates one of its three consistency rules
     * @throws dev.langchain4j.cdi.mcp.server.error.McpArgumentDefinitionException if a {@code @ToolArg} default value
     *     cannot be converted to its parameter type
     */
    public static McpToolDescriptor fromMethod(Class<?> beanClass, Method method) {
        Tool tool = method.getAnnotation(Tool.class);
        String toolName = DEFAULT_NAME.equals(tool.name()) ? method.getName() : tool.name();
        String toolDescription = tool.description();
        McpHeaderValidator.validate(toolName, method);
        McpArguments.validateDefaults("Tool '" + toolName + "'", method);

        McpInputSchema schemaOverride = method.getAnnotation(McpInputSchema.class);
        JsonObject legacySchema;
        JsonObject modernSchema;
        if (schemaOverride != null) {
            JsonObject supplied = McpInputSchemaValidator.parseAndValidate(toolName, method, schemaOverride.value());
            legacySchema = supplied;
            modernSchema = supplied;
        } else {
            legacySchema = JsonSchemaGenerator.fromMethod(method, false);
            modernSchema = JsonSchemaGenerator.fromMethod(method, true);
        }

        return new McpToolDescriptor(
                toolName,
                toolDescription,
                legacySchema,
                modernSchema,
                beanClass,
                method,
                McpIconResolver.resolve(FeatureType.TOOL, toolName, beanClass, method),
                tool.title().isEmpty() ? null : tool.title(),
                McpToolAnnotationsModel.of(tool.annotations()));
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
     * @param modernEra {@code true} for MCP 2026-07-28, which carries the SEP-2243 {@code x-mcp-header} designations,
     *     the {@code icons} member, top-level {@code title}, and {@code annotations}; {@code false} for the 2025-03-26
     *     legacy era, whose {@code Tool} definition has none of them — {@code annotations} is technically defined by
     *     the 2025-03-26 schema too, but this server keeps the legacy wire output byte-identical to the pre-annotations
     *     baseline, as promised by the upstream PR
     * @return an MCP {@link McpToolModel} suitable for JSON serialization
     */
    public McpToolModel toWireFormat(boolean modernEra) {
        return new McpToolModel(
                name,
                modernEra ? annotations : null,
                description,
                modernEra ? modernInputSchema : inputSchema,
                null,
                null,
                null,
                modernEra ? icons : null,
                modernEra ? title : null);
    }

    /**
     * Returns the SEP-2243 {@code x-mcp-header} designations this tool declares, resolved at registration time from the
     * {@link dev.langchain4j.cdi.mcp.server.api.McpHeader} annotations on the backing method.
     *
     * <p>The keys are the argument names used in the tool's input schema and in the {@code tools/call}
     * {@code arguments} object; the values are the header name suffixes, so a designation {@code "Tenant-Id"} is
     * mirrored by a conforming client into the {@code Mcp-Param-Tenant-Id} request header. The MCP 2026-07-28 path
     * validates those headers against the body before dispatching the call; the 2025-03-26 legacy era, which predates
     * SEP-2243, ignores them entirely.
     *
     * @return an unmodifiable map of argument name to designation, empty when this tool designates no argument
     */
    public Map<String, String> getHeaderDesignations() {
        return headerDesignations;
    }

    /**
     * Returns the icons resolved from an {@code org.mcpjava.server.Icons} annotation at registration time.
     *
     * @return the icons, or {@code null} when the tool declares none
     */
    public List<McpIconModel> getIcons() {
        return icons;
    }

    /**
     * Returns the resolved {@code @Tool.title()}.
     *
     * @return the title, or {@code null} when it equals the default ({@code ""})
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the resolved {@code @Tool.annotations()}.
     *
     * @return the annotations, or {@code null} when every member equals its default
     */
    public McpToolAnnotationsModel getAnnotations() {
        return annotations;
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
