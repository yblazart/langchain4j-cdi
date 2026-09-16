package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

/**
 * Wire-format DTO for MCP resource listings, decoupled from the upstream {@code org.mcpjava} model types.
 *
 * @param uri the resource URI
 * @param name the resource name
 * @param description the resource description
 * @param mimeType the resource MIME type
 * @param icons the icons to advertise, or {@code null} to omit the key; only the MCP 2026-07-28 schema carries
 *     {@code icons} on a resource, so the 2025-03-26 legacy era always passes {@code null}
 */
public record McpResourceModel(String uri, String name, String description, String mimeType, List<McpIconModel> icons) {

    /**
     * Creates a resource model carrying no icons. Retained for source compatibility with the pre-icons arity.
     *
     * @param uri the resource URI
     * @param name the resource name
     * @param description the resource description
     * @param mimeType the resource MIME type
     */
    public McpResourceModel(String uri, String name, String description, String mimeType) {
        this(uri, name, description, mimeType, null);
    }

    /**
     * Creates a resource model carrying no icons.
     *
     * @param uri the resource URI
     * @param name the resource name
     * @param description the resource description
     * @param mimeType the resource MIME type
     * @return the wire model
     */
    public static McpResourceModel of(String uri, String name, String description, String mimeType) {
        return new McpResourceModel(uri, name, description, mimeType, null);
    }

    /**
     * Creates a resource model.
     *
     * @param uri the resource URI
     * @param name the resource name
     * @param description the resource description
     * @param mimeType the resource MIME type
     * @param icons the icons to advertise, or {@code null} to omit the key
     * @return the wire model
     */
    public static McpResourceModel of(
            String uri, String name, String description, String mimeType, List<McpIconModel> icons) {
        return new McpResourceModel(uri, name, description, mimeType, icons);
    }
}
