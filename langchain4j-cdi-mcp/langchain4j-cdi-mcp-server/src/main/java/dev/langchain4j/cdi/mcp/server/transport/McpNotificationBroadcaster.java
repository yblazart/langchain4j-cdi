package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Broadcasts MCP notifications to connected SSE streams. Maintains a registry of active SSE channels keyed by session
 * ID and delivers JSON-RPC notifications to one or all connected clients.
 */
@ApplicationScoped
public class McpNotificationBroadcaster {

    private static final Logger LOGGER = Logger.getLogger(McpNotificationBroadcaster.class.getName());

    private final Map<String, McpSseChannel> sseStreams = new ConcurrentHashMap<>();

    @Inject
    McpSubscriptionRegistry subscriptionRegistry;

    /** CDI-required default constructor. */
    public McpNotificationBroadcaster() {}

    /**
     * Registers an SSE output stream for a session.
     *
     * @param sessionId the session identifier
     * @param out the output stream to send notifications to
     * @deprecated raw output streams may be buffered by the Jakarta REST runtime; use {@link #registerStream(String,
     *     McpSseChannel)} with an {@link McpSseEventSinkChannel} instead
     */
    @Deprecated
    public void registerStream(String sessionId, OutputStream out) {
        registerStream(sessionId, new McpSseResponseChannel(out, null));
    }

    /**
     * Registers an SSE channel for a session, replacing any channel previously registered for it.
     *
     * @param sessionId the session identifier
     * @param channel the channel to send notifications to
     */
    public void registerStream(String sessionId, McpSseChannel channel) {
        sseStreams.put(sessionId, channel);
    }

    /**
     * Removes the SSE stream for a session.
     *
     * @param sessionId the session identifier
     */
    public void unregisterStream(String sessionId) {
        sseStreams.remove(sessionId);
    }

    /**
     * Removes the SSE channel for a session, only if it is still the given channel.
     *
     * @param sessionId the session identifier
     * @param channel the channel to remove
     */
    public void unregisterStream(String sessionId, McpSseChannel channel) {
        sseStreams.remove(sessionId, channel);
    }

    /**
     * Sends a notification to all connected SSE streams. Disconnected streams are automatically removed.
     *
     * @param notification the notification object to serialize and broadcast
     */
    public void broadcast(Object notification) {
        String json = serializeToJson(notification);
        sseStreams.entrySet().removeIf(entry -> !deliver(entry.getKey(), entry.getValue(), json));
        dispatchToSubscriptions(notification);
    }

    /**
     * Delivers a change notification to modern {@code subscriptions/listen} streams that opted in to it.
     *
     * @param notification the notification
     */
    public void dispatchToSubscriptions(Object notification) {
        if (subscriptionRegistry != null) {
            subscriptionRegistry.dispatch(notification);
        }
    }

    /**
     * Sends a notification to a specific session's SSE stream.
     *
     * @param sessionId the target session identifier
     * @param notification the notification object to serialize and send
     */
    public void sendToSession(String sessionId, Object notification) {
        McpSseChannel channel = sseStreams.get(sessionId);
        if (channel != null && !deliver(sessionId, channel, serializeToJson(notification))) {
            sseStreams.remove(sessionId, channel);
        }
    }

    /**
     * Returns the number of currently connected SSE streams.
     *
     * @return the count of active SSE streams
     */
    public int connectedStreamCount() {
        return sseStreams.size() + (subscriptionRegistry != null ? subscriptionRegistry.size() : 0);
    }

    /** Closes every registered session stream, releasing the requests that serve them. */
    @PreDestroy
    public void shutdown() {
        sseStreams.values().forEach(McpSseChannel::close);
        sseStreams.clear();
    }

    private static boolean deliver(String sessionId, McpSseChannel channel, String json) {
        channel.sendData(json);
        if (channel.isOpen()) {
            return true;
        }
        LOGGER.log(Level.FINE, "MCP: Removing disconnected SSE stream: {0}", sessionId);
        channel.close();
        return false;
    }

    private String serializeToJson(Object obj) {
        JsonbConfig config = new JsonbConfig().withNullValues(false);
        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            return jsonb.toJson(obj);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "MCP: Failed to serialize notification", e);
            return "{}";
        }
    }
}
