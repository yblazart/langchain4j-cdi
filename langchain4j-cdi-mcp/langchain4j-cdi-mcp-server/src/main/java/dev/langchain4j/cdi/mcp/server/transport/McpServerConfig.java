package dev.langchain4j.cdi.mcp.server.transport;

import java.time.Duration;
import java.util.List;

/** Configuration holder for MCP server identity, providing the server name and version used during initialization. */
public class McpServerConfig {

    /** The server name advertised to clients. Defaults to {@code "langchain4j-cdi"}. */
    private String serverName = "langchain4j-cdi";

    /** The server version advertised to clients. Defaults to {@code "unknown"}. */
    private String serverVersion = "unknown";

    /** The allowed origins for CORS validation. Defaults to empty list. */
    private List<String> allowedOrigins = List.of();

    /** The MRTR mode for handling client interactions. Defaults to {@code REPLAY}. */
    private McpMrtrMode mrtrMode = McpMrtrMode.REPLAY;

    /** The secret for signing request state. Defaults to {@code null}. */
    private String requestStateSecret;

    /** The TTL for request state. Defaults to 10 minutes. */
    private Duration requestStateTtl = Duration.ofMinutes(10);

    /** The timeout for continuation. Defaults to 5 minutes. */
    private Duration continuationTimeout = Duration.ofMinutes(5);

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
     * Returns the allowed origins.
     *
     * @return the allowed origins
     */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Sets the allowed origins.
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

    /** Builder for constructing {@link McpServerConfig} instances. */
    public static class McpServerConfigBuilder {

        private String serverName = "langchain4j-cdi";
        private String serverVersion = "unknown";
        private List<String> allowedOrigins = List.of();
        private McpMrtrMode mrtrMode = McpMrtrMode.REPLAY;
        private String requestStateSecret;
        private Duration requestStateTtl = Duration.ofMinutes(10);
        private Duration continuationTimeout = Duration.ofMinutes(5);

        /** Creates a new builder with default values. */
        public McpServerConfigBuilder() {}

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
         * Sets the allowed origins.
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
            return config;
        }
    }
}
