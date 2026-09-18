package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpSessionException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of MCP sessions including creation, validation, termination, and automatic expiry of idle
 * sessions.
 */
@ApplicationScoped
public class McpSessionManager {

    private static final Logger LOGGER = Logger.getLogger(McpSessionManager.class.getName());
    private static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofMinutes(30);
    private static final long CLEANUP_INTERVAL_SECONDS = 60;

    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();
    private final Duration sessionTimeout;
    private final int maxSessions;
    private final ScheduledExecutorService cleanupExecutor;

    @Inject
    McpServerConfigResolver configResolver;

    @Inject
    McpResourceSubscriptionManager subscriptionManager;

    @Inject
    McpRootsManager rootsManager;

    @Inject
    McpNotificationBroadcaster broadcaster;

    /** CDI-required default constructor. Uses defaults. */
    public McpSessionManager() {
        this(DEFAULT_SESSION_TIMEOUT, McpServerConfig.DEFAULT_MAX_SESSIONS);
    }

    /**
     * Creates a session manager with a custom session timeout and the default max sessions limit.
     *
     * @param sessionTimeout the duration after which idle sessions are expired
     */
    public McpSessionManager(Duration sessionTimeout) {
        this(sessionTimeout, McpServerConfig.DEFAULT_MAX_SESSIONS);
    }

    /**
     * Creates a session manager with a custom session timeout and max sessions limit.
     *
     * @param sessionTimeout the duration after which idle sessions are expired
     * @param maxSessions the maximum number of concurrent sessions (zero or negative for unlimited)
     */
    public McpSessionManager(Duration sessionTimeout, int maxSessions) {
        this.sessionTimeout = sessionTimeout;
        this.maxSessions = maxSessions;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-session-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredSessions, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        cleanupExecutor.shutdownNow();
    }

    /**
     * Creates a new session with a random UUID and stores it.
     *
     * @param initParams the client capabilities from the initialization request
     * @return the new session identifier
     */
    public String createSession(JsonObject initParams) {
        int limit = resolveMaxSessions();
        if (limit > 0 && sessions.size() >= limit) {
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "Too many concurrent sessions", 429, null);
        }
        String id = UUID.randomUUID().toString();
        sessions.put(id, new McpSession(id, initParams));
        return id;
    }

    /**
     * Retrieves and touches the session, or throws if the session is invalid or missing.
     *
     * @param requestId the JSON-RPC request id (used in the exception if thrown)
     * @param sessionId the session identifier from the {@code Mcp-Session-Id} header
     * @return the validated session
     * @throws McpSessionException if the session does not exist
     */
    public McpSession requireSession(Object requestId, String sessionId) {
        if (sessionId == null) {
            throw new McpSessionException(
                    requestId, "Invalid or missing Mcp-Session-Id", McpSessionException.BAD_REQUEST);
        }
        if (!sessions.containsKey(sessionId)) {
            throw new McpSessionException(
                    requestId, "Invalid or missing Mcp-Session-Id", McpSessionException.NOT_FOUND);
        }
        McpSession session = sessions.get(sessionId);
        session.touch();
        return session;
    }

    /**
     * Terminates and removes a session, cleaning up associated subscriptions and roots and closing its notification
     * stream.
     *
     * @param sessionId the session identifier to terminate
     */
    public void terminateSession(String sessionId) {
        McpSession removed = sessions.remove(sessionId);
        if (removed != null) {
            releaseSession(sessionId);
            LOGGER.fine("MCP: Session terminated: " + sessionId);
        }
    }

    private void releaseSession(String sessionId) {
        subscriptionManager.removeSession(sessionId);
        rootsManager.removeSession(sessionId);
        if (broadcaster != null) {
            broadcaster.closeStream(sessionId);
        }
    }

    /**
     * Returns the number of currently active sessions.
     *
     * @return the active session count
     */
    public int activeSessionCount() {
        return sessions.size();
    }

    private int resolveMaxSessions() {
        if (configResolver != null) {
            return configResolver.get().getMaxSessions();
        }
        return maxSessions;
    }

    void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minus(sessionTimeout);
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getLastAccessedAt().isBefore(cutoff)) {
                String sessionId = entry.getKey();
                releaseSession(sessionId);
                LOGGER.info("MCP: Session expired: " + sessionId);
                return true;
            }
            return false;
        });
    }
}
