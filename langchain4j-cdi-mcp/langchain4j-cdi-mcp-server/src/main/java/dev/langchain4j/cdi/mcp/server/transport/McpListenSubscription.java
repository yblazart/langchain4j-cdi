package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.protocol.McpMetaKeys;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.concurrent.CountDownLatch;

/** One open {@code subscriptions/listen} stream. */
public class McpListenSubscription {

    private final Object id;
    private final McpNotificationFilter filter;
    private final McpSseChannel channel;
    private final CountDownLatch closed = new CountDownLatch(1);

    /**
     * Creates a subscription for the given request id, filter and SSE channel.
     *
     * @param id the JSON-RPC id of the {@code subscriptions/listen} request
     * @param filter the notification types the client opted in to
     * @param channel the SSE channel to deliver notifications on
     */
    public McpListenSubscription(Object id, McpNotificationFilter filter, McpSseChannel channel) {
        this.id = id;
        this.filter = filter;
        this.channel = channel;
    }

    /** Sends the {@code notifications/subscriptions/acknowledged} message, the first message of the stream. */
    public void acknowledge() {
        channel.send(notification(
                "notifications/subscriptions/acknowledged",
                Json.createObjectBuilder().add("notifications", filter.toJson())));
        closeIfBroken();
    }

    /**
     * Delivers a notification if this subscription's filter accepts it.
     *
     * @param method the notification's JSON-RPC method
     * @param params the notification's parameters
     * @return {@code false} if the subscription is closed after this call
     */
    public boolean deliver(String method, JsonObject params) {
        if (isClosed()) {
            return false;
        }
        if (filter.accepts(method, params)) {
            JsonObjectBuilder copy = Json.createObjectBuilder(params);
            copy.remove("_meta");
            channel.send(notification(method, copy));
            closeIfBroken();
        }
        return !isClosed();
    }

    /** Sends an SSE keep-alive comment. */
    public void keepAlive() {
        channel.sendComment("keepalive");
        closeIfBroken();
    }

    /** Sends the JSON-RPC completion result and closes the subscription. */
    public void closeGracefully() {
        if (!isClosed()) {
            channel.send(McpModernProtocolHandler.rpcResult(
                    id,
                    Json.createObjectBuilder()
                            .add("resultType", "complete")
                            .add("_meta", meta())
                            .build()));
        }
        close();
    }

    /** Closes the subscription and its channel without sending a completion result. */
    public void close() {
        closed.countDown();
        channel.close();
    }

    /**
     * Blocks until this subscription is closed.
     *
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public void awaitClose() throws InterruptedException {
        closed.await();
    }

    /**
     * Returns whether this subscription is closed.
     *
     * @return {@code true} if closed
     */
    public boolean isClosed() {
        return closed.getCount() == 0;
    }

    private void closeIfBroken() {
        if (!channel.isOpen()) {
            close();
        }
    }

    private JsonObject notification(String method, JsonObjectBuilder params) {
        return Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("method", method)
                .add("params", params.add("_meta", meta()))
                .build();
    }

    private JsonObjectBuilder meta() {
        JsonObjectBuilder meta = Json.createObjectBuilder();
        if (id instanceof Number n) {
            meta.add(McpMetaKeys.SUBSCRIPTION_ID, n.longValue());
        } else {
            meta.add(McpMetaKeys.SUBSCRIPTION_ID, String.valueOf(id));
        }
        return meta;
    }
}
