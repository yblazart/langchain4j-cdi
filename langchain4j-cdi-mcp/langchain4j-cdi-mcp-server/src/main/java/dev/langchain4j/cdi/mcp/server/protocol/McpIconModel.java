package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;
import java.util.Locale;
import org.mcpjava.server.Icon;

/**
 * Wire-format DTO for a single MCP icon, decoupled from the upstream {@link Icon} model type.
 *
 * <p>Mirrors the {@code Icon} definition of the MCP 2026-07-28 schema: {@code src} is required, {@code mimeType},
 * {@code sizes} and {@code theme} are optional. Absent optional members are held as {@code null} so that the JSON-B
 * configuration used by {@link McpJsonSerializer} ({@code withNullValues(false)}) leaves the key out entirely rather
 * than emitting {@code null} or an empty array.
 *
 * @param src the icon URI (an {@code http(s)} URL or a {@code data:} URI); never {@code null}
 * @param mimeType the MIME type override, or {@code null}
 * @param sizes the {@code WxH} sizes (or {@code "any"}) the icon may be used at, or {@code null} when unspecified
 * @param theme {@code "light"}, {@code "dark"}, or {@code null} when the icon suits any theme
 */
public record McpIconModel(String src, String mimeType, List<String> sizes, String theme) {

    /**
     * Converts an upstream {@link Icon} to its wire form, collapsing empty optionals and the empty size list to
     * {@code null} so they are omitted from the JSON.
     *
     * @param icon the icon supplied by an {@code org.mcpjava.server.IconProvider}
     * @return the wire-format icon
     */
    public static McpIconModel of(Icon icon) {
        List<String> sizes = icon.sizes();
        return new McpIconModel(
                icon.src(),
                icon.mimeType().orElse(null),
                sizes == null || sizes.isEmpty() ? null : List.copyOf(sizes),
                icon.theme().map(t -> t.name().toLowerCase(Locale.ROOT)).orElse(null));
    }
}
