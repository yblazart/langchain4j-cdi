package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.logging.Logger;

/** Shared MRTR configuration and helpers. */
@ApplicationScoped
public class McpMrtrSupport {

    private static final Logger LOGGER = Logger.getLogger(McpMrtrSupport.class.getName());

    @Inject
    McpServerConfigResolver config;

    private volatile McpRequestStateCodec codec;

    /** CDI constructor. */
    public McpMrtrSupport() {}

    public McpMrtrSupport(McpServerConfigResolver config) {
        this.config = config;
    }

    public McpRequestStateCodec codec() {
        McpRequestStateCodec current = codec;
        if (current == null) {
            synchronized (this) {
                if (codec == null) {
                    codec = new McpRequestStateCodec(secret(), Clock.systemUTC());
                }
                current = codec;
            }
        }
        return current;
    }

    public McpMrtrMode mode() {
        return config.get().getMrtrMode();
    }

    public Duration stateTtl() {
        return config.get().getRequestStateTtl();
    }

    public Duration continuationTimeout() {
        return config.get().getContinuationTimeout();
    }

    public static String mrtrName(String method, JsonObject params) {
        return params.getString("resources/read".equals(method) ? "uri" : "name", "");
    }

    public static String argumentsDigest(String method, JsonObject params) {
        return McpJsonCanonicalizer.digest(params.get("resources/read".equals(method) ? "uri" : "arguments"));
    }

    private byte[] secret() {
        String configured = config.get().getRequestStateSecret();
        if (configured == null) {
            LOGGER.warning("MCP: no requestStateSecret configured, using a random per-JVM secret. "
                    + "Configure McpServerConfig.requestStateSecret when running several instances.");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("MCP requestStateSecret must be at least 32 characters");
        }
        return bytes;
    }
}
