package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

/**
 * Wire-format DTO for MCP prompt listings, decoupled from the upstream {@code org.mcpjava} model types.
 *
 * @param name the prompt name
 * @param description the prompt description
 * @param arguments the prompt arguments
 * @param icons the icons to advertise, or {@code null} to omit the key; only the MCP 2026-07-28 schema carries
 *     {@code icons} on a prompt, so the 2025-03-26 legacy era always passes {@code null}
 */
public record McpPromptModel(
        String name, String description, List<McpPromptArgument> arguments, List<McpIconModel> icons) {

    /**
     * Creates a prompt model carrying no icons. Retained for source compatibility with the pre-icons arity.
     *
     * @param name the prompt name
     * @param description the prompt description
     * @param arguments the prompt arguments
     */
    public McpPromptModel(String name, String description, List<McpPromptArgument> arguments) {
        this(name, description, arguments, null);
    }

    /**
     * Creates a prompt model carrying no icons.
     *
     * @param name the prompt name
     * @param description the prompt description
     * @param arguments the prompt arguments
     * @return the wire model
     */
    public static McpPromptModel of(String name, String description, List<McpPromptArgument> arguments) {
        return new McpPromptModel(name, description, arguments, null);
    }

    /**
     * Creates a prompt model.
     *
     * @param name the prompt name
     * @param description the prompt description
     * @param arguments the prompt arguments
     * @param icons the icons to advertise, or {@code null} to omit the key
     * @return the wire model
     */
    public static McpPromptModel of(
            String name, String description, List<McpPromptArgument> arguments, List<McpIconModel> icons) {
        return new McpPromptModel(name, description, arguments, icons);
    }
}
