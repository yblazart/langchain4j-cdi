package dev.langchain4j.cdi.mcp.server.transport;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validates the {@code Origin} header to prevent DNS rebinding attacks. */
public final class McpOriginValidator {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private McpOriginValidator() {}

    /**
     * Returns whether a request with this origin may be processed.
     *
     * <p>A request without an {@code Origin} header (non-browser client) is always allowed. With a non-empty
     * {@code allowedOrigins}, a present origin must be listed (or the list must contain {@code *}). With an empty
     * {@code allowedOrigins}, a present origin is allowed only when both the origin's host and the {@code Host}
     * header's host are loopback addresses ({@code localhost}, {@code 127.0.0.1}, {@code ::1}); comparing the origin
     * with the {@code Host} header alone would not stop DNS rebinding, where both carry the attacker's host name.
     *
     * @param origin the {@code Origin} header, may be {@code null}
     * @param host the {@code Host} header, may be {@code null}
     * @param allowedOrigins configured allow-list; empty means "loopback origin on a loopback host only"
     * @return {@code true} if allowed
     */
    public static boolean isAllowed(String origin, String host, List<String> allowedOrigins) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            return allowedOrigins.contains("*") || allowedOrigins.contains(origin);
        }
        return isLoopback(originHost(origin)) && isLoopback(hostHeaderHost(host));
    }

    private static String originHost(String origin) {
        try {
            return URI.create(origin.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Extracts the host of a {@code Host} header value, dropping the port and keeping IPv6 literals bracketed.
     *
     * @param host the {@code Host} header value, may be {@code null}
     * @return the host part, or {@code null}
     */
    private static String hostHeaderHost(String host) {
        if (host == null) {
            return null;
        }
        String value = host.trim();
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end < 0 ? null : value.substring(0, end + 1);
        }
        int colon = value.indexOf(':');
        if (colon >= 0 && colon == value.lastIndexOf(':')) {
            return value.substring(0, colon);
        }
        return value;
    }

    private static boolean isLoopback(String host) {
        return host != null && LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }
}
