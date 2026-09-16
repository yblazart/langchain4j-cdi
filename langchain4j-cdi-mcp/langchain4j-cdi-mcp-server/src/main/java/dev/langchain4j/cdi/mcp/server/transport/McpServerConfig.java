package dev.langchain4j.cdi.mcp.server.transport;

import java.time.Duration;
import java.util.List;

/** Configuration holder for MCP server identity, providing the server name and version used during initialization. */
public class McpServerConfig {

    /** The server name advertised to clients. Defaults to {@code "langchain4j-cdi"}. */
    private String serverName = "langchain4j-cdi";

    /** The server version advertised to clients. Defaults to {@code "unknown"}. */
    private String serverVersion = "unknown";

    /**
     * The {@code Origin} header values accepted by the DNS rebinding protection ({@code *} accepts all). Defaults to an
     * empty list, which accepts a present {@code Origin} only when both its host and the {@code Host} header's host are
     * loopback.
     */
    private List<String> allowedOrigins = List.of();

    /** The MRTR mode for handling client interactions. Defaults to {@code REPLAY}. */
    private McpMrtrMode mrtrMode = McpMrtrMode.REPLAY;

    /** The secret for signing request state. Defaults to {@code null}. */
    private String requestStateSecret;

    /** The TTL for request state. Defaults to 10 minutes. */
    private Duration requestStateTtl = Duration.ofMinutes(10);

    /** The timeout for continuation. Defaults to 5 minutes. */
    private Duration continuationTimeout = Duration.ofMinutes(5);

    /**
     * The SEP-2549 {@code ttlMs} caching hint emitted on cacheable results ({@code tools/list}, {@code prompts/list},
     * {@code resources/list}, {@code resources/templates/list} and {@code resources/read}) of the 2026-07-28 era.
     * Defaults to zero, which tells the client to consider the result immediately stale.
     */
    private Duration cacheTtl = Duration.ZERO;

    /**
     * The SEP-2549 {@code cacheScope} caching hint emitted on cacheable results of the 2026-07-28 era, either
     * {@code "public"} (no user-specific data, shared caches may reuse it) or {@code "private"}. Defaults to
     * {@code "public"}.
     */
    private String cacheScope = CACHE_SCOPE_PUBLIC;

    /** Value of {@link #getCacheScope()} for a result that carries no user-specific data. */
    public static final String CACHE_SCOPE_PUBLIC = "public";

    /** Value of {@link #getCacheScope()} for a result that may only be reused in the same authorization context. */
    public static final String CACHE_SCOPE_PRIVATE = "private";

    /** Creates a config with default server name and version. */
    public McpServerConfig() {}

    /**
     * Creates a config with the specified server name and version.
     *
     * @param serverName the server name
     * @param serverVersion the server version
     */
    public McpServerConfig(String serverName, String serverVersion) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    /**
     * Returns a new builder for constructing an {@link McpServerConfig}.
     *
     * @return a new builder instance
     */
    public static McpServerConfigBuilder builder() {
        return new McpServerConfigBuilder();
    }

    /**
     * Returns the server name.
     *
     * @return the server name
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Sets the server name.
     *
     * @param serverName the server name
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * Returns the server version.
     *
     * @return the server version
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * Sets the server version.
     *
     * @param serverVersion the server version
     */
    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    /**
     * Returns the {@code Origin} header values accepted by the DNS rebinding protection. An empty list accepts a
     * present {@code Origin} only when both its host and the {@code Host} header's host are loopback; {@code *} accepts
     * all.
     *
     * @return the allowed origins
     */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Sets the {@code Origin} header values accepted by the DNS rebinding protection ({@code *} accepts all).
     *
     * @param origins the allowed origins
     */
    public void setAllowedOrigins(List<String> origins) {
        this.allowedOrigins = List.copyOf(origins == null ? List.of() : origins);
    }

    /**
     * Returns the MRTR mode.
     *
     * @return the MRTR mode
     */
    public McpMrtrMode getMrtrMode() {
        return mrtrMode;
    }

    /**
     * Sets the MRTR mode.
     *
     * @param mrtrMode the MRTR mode
     */
    public void setMrtrMode(McpMrtrMode mrtrMode) {
        this.mrtrMode = mrtrMode;
    }

    /**
     * Returns the request state secret.
     *
     * @return the request state secret
     */
    public String getRequestStateSecret() {
        return requestStateSecret;
    }

    /**
     * Sets the request state secret.
     *
     * @param requestStateSecret the request state secret
     */
    public void setRequestStateSecret(String requestStateSecret) {
        this.requestStateSecret = requestStateSecret;
    }

    /**
     * Returns the request state TTL.
     *
     * @return the request state TTL
     */
    public Duration getRequestStateTtl() {
        return requestStateTtl;
    }

    /**
     * Sets the request state TTL.
     *
     * @param requestStateTtl the request state TTL
     */
    public void setRequestStateTtl(Duration requestStateTtl) {
        this.requestStateTtl = requestStateTtl;
    }

    /**
     * Returns the continuation timeout.
     *
     * @return the continuation timeout
     */
    public Duration getContinuationTimeout() {
        return continuationTimeout;
    }

    /**
     * Sets the continuation timeout.
     *
     * @param continuationTimeout the continuation timeout
     */
    public void setContinuationTimeout(Duration continuationTimeout) {
        this.continuationTimeout = continuationTimeout;
    }

    /**
     * Returns the SEP-2549 {@code ttlMs} caching hint emitted on cacheable 2026-07-28 results.
     *
     * @return the cache TTL, never negative
     */
    public Duration getCacheTtl() {
        return cacheTtl;
    }

    /**
     * Sets the SEP-2549 {@code ttlMs} caching hint emitted on cacheable 2026-07-28 results. A {@code null} or negative
     * value is read as zero, the value the schema gives to an immediately stale result.
     *
     * @param cacheTtl the cache TTL
     */
    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl == null || cacheTtl.isNegative() ? Duration.ZERO : cacheTtl;
    }

    /**
     * Returns the SEP-2549 {@code cacheScope} caching hint emitted on cacheable 2026-07-28 results.
     *
     * @return {@value #CACHE_SCOPE_PUBLIC} or {@value #CACHE_SCOPE_PRIVATE}
     */
    public String getCacheScope() {
        return cacheScope;
    }

    /**
     * Sets the SEP-2549 {@code cacheScope} caching hint emitted on cacheable 2026-07-28 results. The schema allows only
     * {@value #CACHE_SCOPE_PUBLIC} and {@value #CACHE_SCOPE_PRIVATE}; anything else falls back to
     * {@value #CACHE_SCOPE_PUBLIC} rather than putting an invalid value on the wire.
     *
     * @param cacheScope the cache scope
     */
    public void setCacheScope(String cacheScope) {
        this.cacheScope = CACHE_SCOPE_PRIVATE.equals(cacheScope) ? CACHE_SCOPE_PRIVATE : CACHE_SCOPE_PUBLIC;
    }

    /** Builder for constructing {@link McpServerConfig} instances. */
    public static class McpServerConfigBuilder {

        private String serverName = "langchain4j-cdi";
        private String serverVersion = "unknown";
        private List<String> allowedOrigins = List.of();
        private McpMrtrMode mrtrMode = McpMrtrMode.REPLAY;
        private String requestStateSecret;
        private Duration requestStateTtl = Duration.ofMinutes(10);
        private Duration continuationTimeout = Duration.ofMinutes(5);
        private Duration cacheTtl = Duration.ZERO;
        private String cacheScope = CACHE_SCOPE_PUBLIC;

        /** Creates a new builder with default values. */
        public McpServerConfigBuilder() {}

        /**
         * Sets the SEP-2549 {@code ttlMs} caching hint emitted on cacheable 2026-07-28 results.
         *
         * @param cacheTtl the cache TTL
         * @return this builder
         */
        public McpServerConfigBuilder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        /**
         * Sets the SEP-2549 {@code cacheScope} caching hint emitted on cacheable 2026-07-28 results.
         *
         * @param cacheScope {@value #CACHE_SCOPE_PUBLIC} or {@value #CACHE_SCOPE_PRIVATE}
         * @return this builder
         */
        public McpServerConfigBuilder cacheScope(String cacheScope) {
            this.cacheScope = cacheScope;
            return this;
        }

        /**
         * Sets the server name.
         *
         * @param serverName the server name
         * @return this builder
         */
        public McpServerConfigBuilder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        /**
         * Sets the server version.
         *
         * @param serverVersion the server version
         * @return this builder
         */
        public McpServerConfigBuilder serverVersion(String serverVersion) {
            this.serverVersion = serverVersion;
            return this;
        }

        /**
         * Sets the {@code Origin} header values accepted by the DNS rebinding protection ({@code *} accepts all).
         *
         * @param allowedOrigins the allowed origins
         * @return this builder
         */
        public McpServerConfigBuilder allowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
            return this;
        }

        /**
         * Sets the MRTR mode.
         *
         * @param mrtrMode the MRTR mode
         * @return this builder
         */
        public McpServerConfigBuilder mrtrMode(McpMrtrMode mrtrMode) {
            this.mrtrMode = mrtrMode;
            return this;
        }

        /**
         * Sets the request state secret.
         *
         * @param requestStateSecret the request state secret
         * @return this builder
         */
        public McpServerConfigBuilder requestStateSecret(String requestStateSecret) {
            this.requestStateSecret = requestStateSecret;
            return this;
        }

        /**
         * Sets the request state TTL.
         *
         * @param requestStateTtl the request state TTL
         * @return this builder
         */
        public McpServerConfigBuilder requestStateTtl(Duration requestStateTtl) {
            this.requestStateTtl = requestStateTtl;
            return this;
        }

        /**
         * Sets the continuation timeout.
         *
         * @param continuationTimeout the continuation timeout
         * @return this builder
         */
        public McpServerConfigBuilder continuationTimeout(Duration continuationTimeout) {
            this.continuationTimeout = continuationTimeout;
            return this;
        }

        /**
         * Builds the {@link McpServerConfig}.
         *
         * @return the constructed config
         */
        public McpServerConfig build() {
            McpServerConfig config = new McpServerConfig(serverName, serverVersion);
            config.setAllowedOrigins(allowedOrigins);
            config.setMrtrMode(mrtrMode);
            config.setRequestStateSecret(requestStateSecret);
            config.setRequestStateTtl(requestStateTtl);
            config.setContinuationTimeout(continuationTimeout);
            config.setCacheTtl(cacheTtl);
            config.setCacheScope(cacheScope);
            return config;
        }
    }
}
