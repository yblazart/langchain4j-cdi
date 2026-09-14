package dev.langchain4j.cdi.mcp.server.transport;

/** Legacy behaviour: request-related notifications are broadcast to the session GET streams. */
public class McpBroadcastResponseChannel implements McpResponseChannel {

    private final McpNotificationBroadcaster broadcaster;

    /**
     * Creates a channel that broadcasts to all connected SSE streams.
     *
     * @param broadcaster the notification broadcaster
     */
    public McpBroadcastResponseChannel(McpNotificationBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void send(Object message) {
        if (broadcaster != null && broadcaster.connectedStreamCount() > 0) {
            broadcaster.broadcast(message);
        }
    }
}
