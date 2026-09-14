package dev.langchain4j.cdi.mcp.server.logging;

import dev.langchain4j.cdi.mcp.server.transport.McpNotificationBroadcaster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

/** Injectable MCP logger that sends log notifications to connected clients via {@code notifications/message}. */
@ApplicationScoped
public class McpLogger implements McpLogSink {

    /** CDI-required default constructor. */
    public McpLogger() {}

    @Inject
    McpNotificationBroadcaster broadcaster;

    private volatile McpLogLevel minimumLevel = McpLogLevel.debug;

    /**
     * Sets the minimum log level; messages below this level are discarded.
     *
     * @param level the minimum level to broadcast
     */
    public void setMinimumLevel(McpLogLevel level) {
        this.minimumLevel = level;
    }

    /**
     * Returns the current minimum log level.
     *
     * @return the minimum level
     */
    public McpLogLevel getMinimumLevel() {
        return minimumLevel;
    }

    @Override
    public McpLogLevel minimumLevel() {
        return minimumLevel;
    }

    /**
     * Builds a {@code notifications/message} JSON-RPC notification.
     *
     * @param level level
     * @param loggerName logger name
     * @param message message
     * @return the notification as a map
     */
    public static Map<String, Object> notification(McpLogLevel level, String loggerName, String message) {
        return Map.of(
                "jsonrpc", "2.0",
                "method", "notifications/message",
                "params", Map.of("level", level.name(), "logger", loggerName, "data", message));
    }

    /**
     * Logs a message at {@link McpLogLevel#debug} level.
     *
     * @param loggerName the logger name included in the notification
     * @param message the log message
     */
    public void debug(String loggerName, String message) {
        log(McpLogLevel.debug, loggerName, message);
    }

    /**
     * Logs a message at {@link McpLogLevel#info} level.
     *
     * @param loggerName the logger name included in the notification
     * @param message the log message
     */
    public void info(String loggerName, String message) {
        log(McpLogLevel.info, loggerName, message);
    }

    /**
     * Logs a message at {@link McpLogLevel#warning} level.
     *
     * @param loggerName the logger name included in the notification
     * @param message the log message
     */
    public void warning(String loggerName, String message) {
        log(McpLogLevel.warning, loggerName, message);
    }

    /**
     * Logs a message at {@link McpLogLevel#error} level.
     *
     * @param loggerName the logger name included in the notification
     * @param message the log message
     */
    public void error(String loggerName, String message) {
        log(McpLogLevel.error, loggerName, message);
    }

    /**
     * Logs a message at the given level, broadcasting it to all connected MCP clients.
     *
     * @param level the log level
     * @param loggerName the logger name included in the notification
     * @param message the log message
     */
    @Override
    public void log(McpLogLevel level, String loggerName, String message) {
        if (level.ordinal() < minimumLevel.ordinal()) {
            return;
        }
        if (broadcaster == null || broadcaster.connectedStreamCount() == 0) {
            return;
        }
        broadcaster.broadcast(notification(level, loggerName, message));
    }
}
