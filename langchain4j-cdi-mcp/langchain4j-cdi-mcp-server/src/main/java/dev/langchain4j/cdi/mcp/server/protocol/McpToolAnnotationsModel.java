package dev.langchain4j.cdi.mcp.server.protocol;

import org.mcpjava.server.tools.Tool;

/**
 * Wire-format DTO for the {@code ToolAnnotations} object of the MCP 2026-07-28 (and 2025-03-26) schema, decoupled from
 * the upstream {@link Tool.Annotations} annotation type.
 *
 * <p>{@link Tool.Annotations}'s four boolean members always resolve to a value through reflection — Java annotations
 * cannot distinguish "left unspecified" from "explicitly set to the default" — so a member is carried here only when it
 * differs from the spec default ({@code readOnlyHint=false}, {@code destructiveHint=true},
 * {@code idempotentHint=false}, {@code openWorldHint=true}); emitting a member that matches its default would assert,
 * with no way to tell, something the tool author may never have written. Absent members are held as {@code null} so
 * that the JSON-B configuration used by {@link McpJsonSerializer} ({@code withNullValues(false)}) leaves the key out
 * entirely.
 *
 * @param title a human-readable title for the tool, or {@code null} when it equals the default ({@code ""})
 * @param readOnlyHint {@code true}/{@code false} when it differs from the default ({@code false}), else {@code null}
 * @param destructiveHint {@code true}/{@code false} when it differs from the default ({@code true}), else {@code null}
 * @param idempotentHint {@code true}/{@code false} when it differs from the default ({@code false}), else {@code null}
 * @param openWorldHint {@code true}/{@code false} when it differs from the default ({@code true}), else {@code null}
 */
public record McpToolAnnotationsModel(
        String title, Boolean readOnlyHint, Boolean destructiveHint, Boolean idempotentHint, Boolean openWorldHint) {

    /**
     * Resolves an upstream {@link Tool.Annotations} to its wire form, keeping only the members that differ from the MCP
     * spec default.
     *
     * @param annotations the annotation instance read by reflection from a {@code @Tool} method; every member is always
     *     populated (explicitly or by its {@code AnnotationDefault})
     * @return the wire-format annotations, or {@code null} when every member equals its default — in which case the
     *     whole {@code annotations} key should be omitted rather than emitting an empty object
     */
    public static McpToolAnnotationsModel of(Tool.Annotations annotations) {
        String title = annotations.title().isEmpty() ? null : annotations.title();
        // Defaults: readOnlyHint=false, destructiveHint=true, idempotentHint=false, openWorldHint=true.
        Boolean readOnlyHint = annotations.readOnlyHint() ? Boolean.TRUE : null;
        Boolean destructiveHint = annotations.destructiveHint() ? null : Boolean.FALSE;
        Boolean idempotentHint = annotations.idempotentHint() ? Boolean.TRUE : null;
        Boolean openWorldHint = annotations.openWorldHint() ? null : Boolean.FALSE;
        if (title == null
                && readOnlyHint == null
                && destructiveHint == null
                && idempotentHint == null
                && openWorldHint == null) {
            return null;
        }
        return new McpToolAnnotationsModel(title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint);
    }
}
