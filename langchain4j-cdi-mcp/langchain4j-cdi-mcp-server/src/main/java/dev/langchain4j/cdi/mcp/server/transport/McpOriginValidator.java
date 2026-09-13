package dev.langchain4j.cdi.mcp.server.transport;

import java.net.URI;
import java.util.List;
import java.util.Set;

/** Validates the {@code Origin} header to prevent DNS rebinding attacks. */
public final class McpOriginValidator {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private McpOriginValidator() {}

    /**
     * Returns whether a request with this origin may be processed.
     *
     * @param origin the {@code Origin} header, may be {@code null}
     * @param host the {@code Host} header, may be {@code null}
     * @param allowedOrigins configured allow-list; empty means "loopback or same host"
     * @return {@code true} if allowed
     */
    public static boolean isAllowed(String origin, String host, List<String> allowedOrigins) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            return allowedOrigins.contains("*") || allowedOrigins.contains(origin);
        }
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String originHost = uri.getHost();
        if (originHost == null) {
            return false;
        }
        if (LOOPBACK_HOSTS.contains(originHost)) {
            return true;
        }
        String authority = uri.getPort() == -1 ? originHost : originHost + ":" + uri.getPort();
        return host != null && (host.equalsIgnoreCase(authority) || host.equalsIgnoreCase(originHost));
    }
}
