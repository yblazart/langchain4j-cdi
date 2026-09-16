package dev.langchain4j.cdi.mcp.server.logging;

import dev.langchain4j.cdi.mcp.server.transport.McpResponseChannel;

/**
 * Modern era: log notifications are emitted on the request stream, only when the request carried
 * {@code io.modelcontextprotocol/logLevel}, and only at or above that level.
 */
public class McpRequestLogSink implements McpLogSink {

    private final McpResponseChannel channel;
    private final McpLogLevel requestedLevel;

    /**
     * Creates a sink that emits log notifications on a single request's response channel.
     *
     * @param channel the request response channel
     * @param requestedLevel the minimum level requested for this request, or {@code null} for none
     */
    public McpRequestLogSink(McpResponseChannel channel, McpLogLevel requestedLevel) {
        this.channel = channel;
        this.requestedLevel = requestedLevel;
    }

    @Override
    public void log(McpLogLevel level, String loggerName, String message) {
        if (requestedLevel == null || level.ordinal() < requestedLevel.ordinal()) {
            return;
        }
        channel.send(McpLogger.notification(level, loggerName, message));
    }

    @Override
    public McpLogLevel minimumLevel() {
        return requestedLevel != null ? requestedLevel : McpLogLevel.emergency;
    }
}
