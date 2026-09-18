package dev.langchain4j.cdi.mcp.server.schema;

import dev.langchain4j.cdi.mcp.server.api.McpFrameworkTypes;
import dev.langchain4j.cdi.mcp.server.api.McpHeader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects the SEP-2243 {@code x-mcp-header} designations a tool method declares, so that the request half of the SEP
 * can validate the {@code Mcp-Param-<designation>} headers a client mirrors back without re-reading annotations on
 * every call.
 *
 * <p>The map produced here is the request-time twin of the {@code x-mcp-header} keywords
 * {@link JsonSchemaGenerator#fromMethod(Method, boolean)} publishes in the modern-era schema: both walk the same
 * parameters, skip the same framework types, and name arguments with the same
 * {@link McpParameterNames#resolve(Parameter)}.
 */
public final class McpHeaderDesignations {

    private McpHeaderDesignations() {}

    /**
     * Returns the designations of a tool method, keyed by the argument name used in the tool's input schema and in the
     * {@code tools/call} {@code arguments} object.
     *
     * @param method the tool method, may be {@code null}
     * @return an unmodifiable map of argument name to {@code x-mcp-header} value, empty when the method designates none
     */
    public static Map<String, String> of(Method method) {
        if (method == null) {
            return Map.of();
        }
        Map<String, String> designations = new LinkedHashMap<>();
        for (Parameter param : method.getParameters()) {
            McpHeader designation = param.getAnnotation(McpHeader.class);
            if (designation == null || McpFrameworkTypes.isFrameworkType(param.getType())) {
                continue;
            }
            designations.put(McpParameterNames.resolve(param), designation.value());
        }
        return designations.isEmpty() ? Map.of() : Collections.unmodifiableMap(designations);
    }
}
