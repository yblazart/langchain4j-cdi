package dev.langchain4j.cdi.mcp.server.protocol;

import jakarta.json.JsonObject;
import java.util.List;

/**
 * Wire-format DTO for MCP tool listings, decoupled from the upstream {@code org.mcpjava} model types.
 *
 * @param name the tool name
 * @param annotations the tool annotations, or {@code null}
 * @param description the tool description
 * @param inputSchema the JSON Schema of the tool's arguments
 * @param outputSchema the JSON Schema of the tool's structured result, or {@code null}
 * @param returnDirect the {@code returnDirect} hint, or {@code null}
 * @param destructive the {@code destructive} hint, or {@code null}
 * @param icons the icons to advertise, or {@code null} to omit the key; only the MCP 2026-07-28 schema carries
 *     {@code icons} on a tool, so the 2025-03-26 legacy era always passes {@code null}
 */
public record McpToolModel(
        String name,
        Object annotations,
        String description,
        JsonObject inputSchema,
        Object outputSchema,
        Object returnDirect,
        Object destructive,
        List<McpIconModel> icons) {

    /**
     * Creates a tool model carrying no icons. Retained for source compatibility with the pre-icons arity.
     *
     * @param name the tool name
     * @param annotations the tool annotations, or {@code null}
     * @param description the tool description
     * @param inputSchema the JSON Schema of the tool's arguments
     * @param outputSchema the JSON Schema of the tool's structured result, or {@code null}
     * @param returnDirect the {@code returnDirect} hint, or {@code null}
     * @param destructive the {@code destructive} hint, or {@code null}
     */
    public McpToolModel(
            String name,
            Object annotations,
            String description,
            JsonObject inputSchema,
            Object outputSchema,
            Object returnDirect,
            Object destructive) {
        this(name, annotations, description, inputSchema, outputSchema, returnDirect, destructive, null);
    }
}
