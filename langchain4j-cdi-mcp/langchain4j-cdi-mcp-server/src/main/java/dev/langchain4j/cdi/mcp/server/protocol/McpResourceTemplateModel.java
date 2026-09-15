package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

/**
 * Wire-format DTO for MCP resource template listings, decoupled from the upstream {@code org.mcpjava} model types.
 *
 * @param uriTemplate the URI template
 * @param name the resource template name
 * @param description the resource template description
 * @param mimeType the resource MIME type
 * @param icons the icons to advertise, or {@code null} to omit the key; only the MCP 2026-07-28 schema carries
 *     {@code icons} on a resource template, so the 2025-03-26 legacy era always passes {@code null}
 */
public record McpResourceTemplateModel(
        String uriTemplate, String name, String description, String mimeType, List<McpIconModel> icons) {

    /**
     * Creates a resource template model carrying no icons. Retained for source compatibility with the pre-icons arity.
     *
     * @param uriTemplate the URI template
     * @param name the resource template name
     * @param description the resource template description
     * @param mimeType the resource MIME type
     */
    public McpResourceTemplateModel(String uriTemplate, String name, String description, String mimeType) {
        this(uriTemplate, name, description, mimeType, null);
    }

    /**
     * Creates a resource template model carrying no icons.
     *
     * @param uriTemplate the URI template
     * @param name the resource template name
     * @param description the resource template description
     * @param mimeType the resource MIME type
     * @return the wire model
     */
    public static McpResourceTemplateModel of(String uriTemplate, String name, String description, String mimeType) {
        return new McpResourceTemplateModel(uriTemplate, name, description, mimeType, null);
    }

    /**
     * Creates a resource template model.
     *
     * @param uriTemplate the URI template
     * @param name the resource template name
     * @param description the resource template description
     * @param mimeType the resource MIME type
     * @param icons the icons to advertise, or {@code null} to omit the key
     * @return the wire model
     */
    public static McpResourceTemplateModel of(
            String uriTemplate, String name, String description, String mimeType, List<McpIconModel> icons) {
        return new McpResourceTemplateModel(uriTemplate, name, description, mimeType, icons);
    }
}
