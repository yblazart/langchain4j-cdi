package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.protocol.McpJsonSerializer;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Open {@code subscriptions/listen} streams of modern clients. */
@ApplicationScoped
public class McpSubscriptionRegistry {

    static final long KEEP_ALIVE_SECONDS = 25;

    private final Set<McpListenSubscription> subscriptions = ConcurrentHashMap.newKeySet();
    private volatile ScheduledExecutorService keepAliveExecutor;

    /** CDI constructor. */
    public McpSubscriptionRegistry() {}

    /**
     * Opens a new subscription, sending its acknowledgement immediately.
     *
     * @param id the JSON-RPC id of the {@code subscriptions/listen} request
     * @param filter the notification types the client opted in to
     * @param channel the SSE channel to deliver notifications on
     * @return the opened subscription
     */
    public McpListenSubscription open(Object id, McpNotificationFilter filter, McpSseResponseChannel channel) {
        McpListenSubscription subscription = new McpListenSubscription(id, filter, channel);
        subscription.acknowledge();
        if (!subscription.isClosed()) {
            subscriptions.add(subscription);
            startKeepAlive();
        }
        return subscription;
    }

    /**
     * Removes a subscription from the registry, closing it if still open.
     *
     * @param subscription the subscription to remove
     */
    public void remove(McpListenSubscription subscription) {
        subscriptions.remove(subscription);
        subscription.close();
    }

    /**
     * Delivers a change notification to every open subscription whose filter accepts it.
     *
     * @param notification the notification
     */
    public void dispatch(Object notification) {
        if (subscriptions.isEmpty()) {
            return;
        }
        JsonObject json = McpJsonSerializer.toJsonObject(notification);
        String method = json.getString("method", null);
        if (method == null) {
            return;
        }
        JsonObject params = json.get("params") instanceof JsonObject p ? p : JsonValue.EMPTY_JSON_OBJECT;
        subscriptions.removeIf(subscription -> !subscription.deliver(method, params));
    }

    /**
     * Returns the number of currently open subscriptions.
     *
     * @return the count of open subscriptions
     */
    public int size() {
        return subscriptions.size();
    }

    /** Gracefully completes and closes every open subscription, and stops the keep-alive scheduler. */
    @PreDestroy
    public void shutdown() {
        subscriptions.forEach(McpListenSubscription::closeGracefully);
        subscriptions.clear();
        ScheduledExecutorService executor = keepAliveExecutor;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void startKeepAlive() {
        if (keepAliveExecutor != null) {
            return;
        }
        synchronized (this) {
            if (keepAliveExecutor == null) {
                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "mcp-listen-keepalive");
                    t.setDaemon(true);
                    return t;
                });
                executor.scheduleAtFixedRate(
                        () -> subscriptions.removeIf(s -> {
                            s.keepAlive();
                            return s.isClosed();
                        }),
                        KEEP_ALIVE_SECONDS,
                        KEEP_ALIVE_SECONDS,
                        TimeUnit.SECONDS);
                keepAliveExecutor = executor;
            }
        }
    }
}
