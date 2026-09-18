package dev.langchain4j.cdi.mcp.server.schema;

import dev.langchain4j.cdi.mcp.server.api.McpFrameworkTypes;
import dev.langchain4j.cdi.mcp.server.api.McpHeader;
import dev.langchain4j.cdi.mcp.server.error.McpHeaderDefinitionException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the four constraints SEP-2243 puts on an {@code x-mcp-header} argument designation.
 *
 * <p>Validation runs at tool registration, not at call time, and it throws. SEP-2243 requires a client to
 * <em>exclude</em> a tool whose designation violates any constraint from the result of {@code tools/list}: a malformed
 * value therefore produces no error a user would ever see, it just makes the tool silently vanish from the client's
 * catalogue. Failing the deployment is the only place the mistake is visible.
 *
 * @see McpHeader
 */
public final class McpHeaderValidator {

    /** JSON Schema types a designation may be applied to. {@code number} is excluded by the spec. */
    private static final Set<String> PERMITTED_TYPES = Set.of("string", "integer", "boolean");

    private static final String PERMITTED_TYPES_TEXT = "integer, string, boolean";

    private McpHeaderValidator() {}

    /**
     * Validates every {@link McpHeader} designation on the given tool method.
     *
     * @param toolName the tool's advertised name, used in the failure message
     * @param method the {@code @Tool} method to inspect
     * @throws McpHeaderDefinitionException if any designation violates SEP-2243
     */
    public static void validate(String toolName, Method method) {
        Map<String, String> designatedBy = new HashMap<>();

        for (Parameter param : method.getParameters()) {
            McpHeader designation = param.getAnnotation(McpHeader.class);
            if (designation == null) {
                continue;
            }
            String paramName = McpParameterNames.resolve(param);
            String headerName = designation.value();

            if (McpFrameworkTypes.isFrameworkType(param.getType())) {
                throw fail(
                        toolName,
                        paramName,
                        headerName,
                        "the parameter is an MCP framework type injected by the server, so it is not part of the "
                                + "tool's input schema and cannot carry a designation");
            }
            if (headerName.isEmpty()) {
                throw fail(toolName, paramName, headerName, "the x-mcp-header value MUST NOT be empty");
            }
            if (!isLegalHeaderName(headerName)) {
                throw fail(
                        toolName,
                        paramName,
                        headerName,
                        "the x-mcp-header value MUST contain only ASCII characters, excluding space and ':'");
            }
            String jsonType = JsonSchemaGenerator.mapJavaTypeToJsonSchema(param.getType());
            if (!PERMITTED_TYPES.contains(jsonType)) {
                throw fail(
                        toolName,
                        paramName,
                        headerName,
                        "x-mcp-header MUST only be applied to a primitive type (" + PERMITTED_TYPES_TEXT
                                + "); this parameter is " + param.getType().getSimpleName()
                                + ", of JSON Schema type '" + jsonType + "'"
                                + ("number".equals(jsonType) ? ", and type 'number' is explicitly not permitted" : ""));
            }
            String previous = designatedBy.putIfAbsent(headerName.toLowerCase(Locale.ROOT), paramName);
            if (previous != null) {
                throw fail(
                        toolName,
                        paramName,
                        headerName,
                        "the x-mcp-header value MUST be case-insensitively unique within a single tool definition; "
                                + "it is already designated by parameter '" + previous + "'");
            }
        }
    }

    /**
     * ASCII, excluding space and {@code ':'}. Control characters are excluded too: they are ASCII, but never legal in
     * an HTTP field name, so a designation carrying one could never be mirrored into a header.
     */
    private static boolean isLegalHeaderName(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c >= 0x7F || c == ':') {
                return false;
            }
        }
        return true;
    }

    private static McpHeaderDefinitionException fail(
            String toolName, String paramName, String headerName, String rule) {
        return new McpHeaderDefinitionException("Tool '" + toolName + "', parameter '" + paramName
                + "': invalid @McpHeader(\"" + headerName + "\") — " + rule
                + ". SEP-2243 requires clients to exclude a tool with an invalid designation from tools/list, "
                + "so this tool would silently disappear from every conforming client.");
    }
}
