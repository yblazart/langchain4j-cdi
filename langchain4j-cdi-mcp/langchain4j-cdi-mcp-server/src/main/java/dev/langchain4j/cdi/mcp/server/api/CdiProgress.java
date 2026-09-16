package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.transport.McpProgressReporter;
import dev.langchain4j.cdi.mcp.server.transport.McpProgressSink;
import java.util.Optional;
import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.progress.ProgressNotification;
import org.mcpjava.server.progress.ProgressToken;
import org.mcpjava.server.progress.ProgressTracker;

/** Implementation of {@link Progress} that wraps a progress token and delegates to an {@link McpProgressSink}. */
public class CdiProgress implements Progress {

    private final Object rawToken;
    private final McpProgressSink sink;

    /**
     * Creates a new progress wrapper backed by the broadcast progress reporter.
     *
     * @param rawToken the raw progress token, or {@code null} if no token was provided
     * @param progressReporter the progress reporter
     * @deprecated use {@link #CdiProgress(Object, McpProgressSink)}
     */
    @Deprecated
    public CdiProgress(Object rawToken, McpProgressReporter progressReporter) {
        this(rawToken, (McpProgressSink) progressReporter);
    }

    /**
     * Creates a new progress wrapper.
     *
     * @param rawToken the raw progress token, or {@code null} if no token was provided
     * @param sink the sink that receives progress updates
     */
    public CdiProgress(Object rawToken, McpProgressSink sink) {
        this.rawToken = rawToken;
        this.sink = sink;
    }

    @Override
    public Optional<ProgressToken> token() {
        if (rawToken == null) {
            return Optional.empty();
        }
        return Optional.of(CdiProgressToken.of(rawToken));
    }

    @Override
    public ProgressNotification.Builder notificationBuilder() {
        return new CdiProgressNotification.CdiBuilder(rawToken, sink);
    }

    @Override
    public ProgressTracker.Builder trackerBuilder() {
        return new CdiProgressTracker.CdiBuilder(rawToken, sink);
    }
}
