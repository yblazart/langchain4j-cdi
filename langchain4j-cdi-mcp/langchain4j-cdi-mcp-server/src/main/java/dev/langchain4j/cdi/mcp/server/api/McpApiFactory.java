package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.logging.McpLogger;
import dev.langchain4j.cdi.mcp.server.logging.McpRequestLogSink;
import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpElicitationManager;
import dev.langchain4j.cdi.mcp.server.transport.McpLegacyClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpNoopResponseChannel;
import dev.langchain4j.cdi.mcp.server.transport.McpProgressReporter;
import dev.langchain4j.cdi.mcp.server.transport.McpProgressSink;
import dev.langchain4j.cdi.mcp.server.transport.McpResponseChannel;
import dev.langchain4j.cdi.mcp.server.transport.McpRootsManager;
import dev.langchain4j.cdi.mcp.server.transport.McpSamplingManager;
import dev.langchain4j.cdi.mcp.server.transport.McpServerRequestManager;
import dev.langchain4j.cdi.mcp.server.transport.McpSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mcpjava.server.Cancellation;
import org.mcpjava.server.progress.Progress;

/**
 * Factory that creates MCP framework type instances from request context. Used by {@code McpBeanInvoker} to inject
 * framework types into {@code @Tool}, {@code @Prompt}, and {@code @Resource} method parameters.
 */
@ApplicationScoped
public class McpApiFactory {

    /** CDI-required default constructor. */
    public McpApiFactory() {}

    @Inject
    McpLogger mcpLogger;

    @Inject
    McpProgressReporter progressReporter;

    @Inject
    McpRootsManager rootsManager;

    @Inject
    McpSamplingManager samplingManager;

    @Inject
    McpElicitationManager elicitationManager;

    @Inject
    McpServerRequestManager serverRequestManager;

    /**
     * Creates an instance of the given MCP framework type.
     *
     * @param type the framework interface type (McpLog, Progress, etc.)
     * @param ctx the per-request context
     * @param session the MCP session
     * @param beanType the CDI bean type being invoked (used for logger naming)
     * @return the framework type instance
     * @throws IllegalArgumentException if the type is not a known framework type
     */
    public Object createInstance(Class<?> type, McpRequestContext ctx, McpSession session, Class<?> beanType) {
        boolean modern = ctx != null && ctx.isModern();
        McpResponseChannel channel =
                ctx != null && ctx.channel() != null ? ctx.channel() : McpNoopResponseChannel.INSTANCE;
        if (type == McpLog.class) {
            return modern
                    ? new CdiMcpLog(
                            new McpRequestLogSink(channel, ctx.protocol().logLevel()), beanType.getSimpleName())
                    : new CdiMcpLog(mcpLogger, beanType.getSimpleName());
        }
        if (type == Progress.class) {
            Object token = ctx != null ? ctx.progressToken() : null;
            return modern
                    ? new CdiProgress(token, McpProgressSink.forChannel(channel))
                    : new CdiProgress(token, progressReporter);
        }
        if (type == Cancellation.class) {
            return new CdiCancellation(ctx != null ? ctx.cancelledFlag() : null);
        }
        if (type == McpConnection.class) {
            return modern
                    ? new CdiMcpConnection(ctx.protocol(), ctx.requestId())
                    : new CdiMcpConnection(session, mcpLogger);
        }
        McpClientRequester requester = ctx != null && ctx.clientRequester() != null
                ? ctx.clientRequester()
                : new McpLegacyClientRequester(session, serverRequestManager);
        if (type == Roots.class) {
            return new CdiRoots(requester, rootsManager);
        }
        if (type == Sampling.class) {
            return new CdiSampling(requester, samplingManager);
        }
        if (type == Elicitation.class) {
            return new CdiElicitation(requester, elicitationManager);
        }
        if (type == McpInteractions.class) {
            return new CdiInteractions(requester);
        }
        throw new IllegalArgumentException("Unknown MCP framework type: " + type.getName());
    }
}
