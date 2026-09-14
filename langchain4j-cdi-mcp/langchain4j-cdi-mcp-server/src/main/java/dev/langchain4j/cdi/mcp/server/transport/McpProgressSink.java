package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcNotification;

/** Receives progress updates produced by the {@code Progress} API. */
@FunctionalInterface
public interface McpProgressSink {

    /**
     * Reports progress for a given token.
     *
     * @param progressToken the token identifying the operation in progress
     * @param progress the current progress value
     * @param total the total expected value (0 if unknown)
     * @param message an optional human-readable progress message, may be {@code null}
     */
    void reportProgress(Object progressToken, double progress, double total, String message);

    /**
     * Creates a sink that writes {@code notifications/progress} to the given channel.
     *
     * @param channel request response channel
     * @return a sink writing {@code notifications/progress} to the channel
     */
    static McpProgressSink forChannel(McpResponseChannel channel) {
        return (token, progress, total, message) -> {
            if (token != null) {
                channel.send(JsonRpcNotification.progress(token, progress, total, message));
            }
        };
    }
}
