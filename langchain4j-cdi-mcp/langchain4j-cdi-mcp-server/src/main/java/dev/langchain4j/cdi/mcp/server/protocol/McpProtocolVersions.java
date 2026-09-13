package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

/** MCP protocol revisions implemented by this server. */
public final class McpProtocolVersions {

    /** Stateless revision using per-request {@code _meta}. */
    public static final String MODERN_2026_07_28 = "2026-07-28";

    /** Handshake-based revision using {@code initialize} and sessions. */
    public static final String LEGACY_2025_03_26 = "2025-03-26";

    /** Modern revisions implemented, most recent first. */
    public static final List<String> MODERN = List.of(MODERN_2026_07_28);

    /** All revisions implemented, most recent first, as advertised by {@code server/discover}. */
    public static final List<String> SUPPORTED = List.of(MODERN_2026_07_28, LEGACY_2025_03_26);

    private McpProtocolVersions() {}

    /**
     * Returns whether the version is a modern revision implemented by this server.
     *
     * @param version the protocol version
     * @return {@code true} if supported and modern
     */
    public static boolean isSupportedModern(String version) {
        return version != null && MODERN.contains(version);
    }

    /**
     * Returns whether the version belongs to the modern era (revision 2026-07-28 or later). Protocol versions are ISO
     * dates, so lexicographic comparison is chronological.
     *
     * @param version the protocol version
     * @return {@code true} if modern era
     */
    public static boolean isModernEra(String version) {
        return version != null && version.compareTo(MODERN_2026_07_28) >= 0;
    }
}
