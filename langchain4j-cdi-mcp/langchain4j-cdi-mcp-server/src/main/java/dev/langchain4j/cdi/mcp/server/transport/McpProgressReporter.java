package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcNotification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reports progress for long-running MCP operations. When a request includes {@code _meta.progressToken}, the server can
 * send {@code notifications/progress} to the client via SSE.
 */
@ApplicationScoped
public class McpProgressReporter {

    @Inject
    McpNotificationBroadcaster broadcaster;

    /**
     * Reports progress for a given token. Has no effect if the broadcaster is not available or no streams are
     * connected.
     *
     * @param progressToken the token from the request's {@code _meta.progressToken}
     * @param progress current progress value
     * @param total total expected value (0 if unknown)
     */
    public void reportProgress(Object progressToken, double progress, double total) {
        if (progressToken == null || broadcaster == null || broadcaster.connectedStreamCount() == 0) {
            return;
        }
        broadcaster.broadcast(JsonRpcNotification.progress(progressToken, progress, total));
    }
}
