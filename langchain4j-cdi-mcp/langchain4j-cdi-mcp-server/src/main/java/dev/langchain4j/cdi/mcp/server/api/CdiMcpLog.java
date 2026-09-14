package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import dev.langchain4j.cdi.mcp.server.logging.McpLogSink;
import dev.langchain4j.cdi.mcp.server.logging.McpLogger;

/** Implementation of {@link McpLog} that delegates to an {@link McpLogSink}. */
public class CdiMcpLog implements McpLog {

    private final McpLogSink sink;
    private final String loggerName;

    /**
     * Creates a new MCP log wrapper backed by the internal broadcast logger.
     *
     * @param mcpLogger the internal MCP logger
     * @param loggerName the logger name used for log entries
     * @deprecated use {@link #CdiMcpLog(McpLogSink, String)}
     */
    @Deprecated
    public CdiMcpLog(McpLogger mcpLogger, String loggerName) {
        this((McpLogSink) mcpLogger, loggerName);
    }

    /**
     * Creates a new MCP log wrapper.
     *
     * @param sink the log sink to send log entries to
     * @param loggerName the logger name used for log entries
     */
    public CdiMcpLog(McpLogSink sink, String loggerName) {
        this.sink = sink;
        this.loggerName = loggerName;
    }

    @Override
    public LogLevel level() {
        return toApiLevel(sink.minimumLevel());
    }

    @Override
    public void send(LogLevel level, Object data) {
        sink.log(toInternalLevel(level), loggerName, data != null ? data.toString() : "null");
    }

    @Override
    public void send(LogLevel level, String format, Object... params) {
        sink.log(toInternalLevel(level), loggerName, formatMessage(format, params));
    }

    @Override
    public void debug(String format, Object... params) {
        send(LogLevel.DEBUG, format, params);
    }

    @Override
    public void info(String format, Object... params) {
        send(LogLevel.INFO, format, params);
    }

    @Override
    public void error(String format, Object... params) {
        send(LogLevel.ERROR, format, params);
    }

    @Override
    public void error(Throwable throwable, String format, Object... params) {
        String message = formatMessage(format, params) + " - " + throwable.getMessage();
        send(LogLevel.ERROR, message);
    }

    private static McpLogLevel toInternalLevel(LogLevel level) {
        return McpLogLevel.values()[level.ordinal()];
    }

    private static LogLevel toApiLevel(McpLogLevel level) {
        return LogLevel.values()[level.ordinal()];
    }

    private static String formatMessage(String format, Object... params) {
        if (params == null || params.length == 0) {
            return format;
        }
        String result = format;
        for (Object param : params) {
            int idx = result.indexOf("{}");
            if (idx >= 0) {
                result = result.substring(0, idx) + param + result.substring(idx + 2);
            } else {
                break;
            }
        }
        return result;
    }
}
