package dev.langchain4j.cdi.mcp.server.logging;

/** Receives log messages produced by the {@code McpLog} API. */
public interface McpLogSink {

    /**
     * Logs a message at the given level.
     *
     * @param level the log level
     * @param loggerName the logger name included in the notification
     * @param message the log message
     */
    void log(McpLogLevel level, String loggerName, String message);

    /**
     * Returns the minimum level at or above which messages are sent.
     *
     * @return the minimum level
     */
    McpLogLevel minimumLevel();
}
