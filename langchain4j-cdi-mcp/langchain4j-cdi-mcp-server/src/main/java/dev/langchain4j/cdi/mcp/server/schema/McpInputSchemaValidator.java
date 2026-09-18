package dev.langchain4j.cdi.mcp.server.schema;

import dev.langchain4j.cdi.mcp.server.api.McpFrameworkTypes;
import dev.langchain4j.cdi.mcp.server.api.McpHeader;
import dev.langchain4j.cdi.mcp.server.error.McpInputSchemaDefinitionException;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.Set;

/**
 * Parses and enforces the three consistency rules a {@code @McpInputSchema}-supplied document must satisfy against the
 * method it describes.
 *
 * <p>Validation runs at tool registration, not at call time, and it throws: a hand-written schema that lies about its
 * method — a required argument that can never bind, or a header designation the client is never told about — is a
 * mistake that is only visible if it fails the deployment.
 *
 * @see dev.langchain4j.cdi.mcp.server.api.McpInputSchema
 */
public final class McpInputSchemaValidator {

    private McpInputSchemaValidator() {}

    /**
     * Parses the supplied schema text and validates it against the three {@code @McpInputSchema} rules.
     *
     * @param toolName the tool's advertised name, used in the failure message
     * @param method the {@code @Tool} method the schema describes
     * @param rawSchema the raw JSON Schema text from {@code @McpInputSchema#value()}
     * @return the parsed schema, unchanged, ready to be served verbatim
     * @throws McpInputSchemaDefinitionException if any of the three rules is violated
     */
    public static JsonObject parseAndValidate(String toolName, Method method, String rawSchema) {
        JsonObject schema = parse(toolName, rawSchema);
        requireObjectType(toolName, schema);
        requireBindableRequiredProperties(toolName, method, schema);
        rejectCombinationWithMcpHeader(toolName, method);
        return schema;
    }

    private static JsonObject parse(String toolName, String rawSchema) {
        try {
            JsonValue value = Json.createReader(new StringReader(rawSchema)).readValue();
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw fail(
                        toolName,
                        "the supplied @McpInputSchema value MUST be a JSON object with \"type\": \"object\" at its "
                                + "root; it parsed as a JSON " + value.getValueType());
            }
            return value.asJsonObject();
        } catch (JsonException e) {
            throw fail(toolName, "the supplied @McpInputSchema value is not valid JSON: " + e.getMessage());
        }
    }

    private static void requireObjectType(String toolName, JsonObject schema) {
        JsonValue type = schema.get("type");
        boolean isObjectType = type != null
                && type.getValueType() == JsonValue.ValueType.STRING
                && "object".equals(((JsonString) type).getString());
        if (!isObjectType) {
            throw fail(
                    toolName,
                    "the supplied @McpInputSchema value MUST be a JSON object with \"type\": \"object\" at its root "
                            + "— tool arguments are always a JSON object");
        }
    }

    private static void requireBindableRequiredProperties(String toolName, Method method, JsonObject schema) {
        JsonValue required = schema.get("required");
        if (required == null || required.getValueType() != JsonValue.ValueType.ARRAY) {
            return;
        }
        Set<String> bindableNames = bindableParameterNames(method);
        for (JsonValue entry : required.asJsonArray()) {
            if (entry.getValueType() != JsonValue.ValueType.STRING) {
                continue;
            }
            String propertyName = ((JsonString) entry).getString();
            if (!bindableNames.contains(propertyName)) {
                throw fail(
                        toolName,
                        "required property '" + propertyName + "' has no bindable Java parameter of that name; "
                                + "arguments are bound by parameter name at call time, so an advertised required "
                                + "argument that can never bind would be a silent failure for the client");
            }
        }
    }

    private static void rejectCombinationWithMcpHeader(String toolName, Method method) {
        for (Parameter param : method.getParameters()) {
            if (param.isAnnotationPresent(McpHeader.class)) {
                throw fail(
                        toolName,
                        "@McpInputSchema cannot be combined with @McpHeader on the same tool; SEP-2243 request "
                                + "validation reads header designations from the reflected @McpHeader annotations, "
                                + "independently of the hand-written schema, so the two could drift and validate "
                                + "headers the client was never told about");
            }
        }
    }

    private static Set<String> bindableParameterNames(Method method) {
        Set<String> names = new HashSet<>();
        for (Parameter param : method.getParameters()) {
            if (McpFrameworkTypes.isFrameworkType(param.getType())) {
                continue;
            }
            names.add(McpParameterNames.resolve(param));
        }
        return names;
    }

    private static McpInputSchemaDefinitionException fail(String toolName, String rule) {
        return new McpInputSchemaDefinitionException("Tool '" + toolName + "': invalid @McpInputSchema — " + rule);
    }
}
