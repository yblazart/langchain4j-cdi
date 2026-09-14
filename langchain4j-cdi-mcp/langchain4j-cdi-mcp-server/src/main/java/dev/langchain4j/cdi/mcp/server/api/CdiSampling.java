package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpLegacyClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpSamplingManager;
import dev.langchain4j.cdi.mcp.server.transport.McpSession;

/** Implementation of {@link Sampling} that delegates to {@link McpSamplingManager}. */
public class CdiSampling implements Sampling {

    private final McpClientRequester requester;
    private final McpSamplingManager samplingManager;

    /**
     * Creates a new sampling wrapper bound to a legacy session.
     *
     * @param session the MCP session
     * @param samplingManager the sampling manager
     * @param sessionId the session identifier
     * @deprecated use {@link #CdiSampling(McpClientRequester, McpSamplingManager)}
     */
    @Deprecated
    public CdiSampling(McpSession session, McpSamplingManager samplingManager, String sessionId) {
        this(new McpLegacyClientRequester(session, samplingManager.getRequestManager()), samplingManager);
    }

    /**
     * Creates a new sampling wrapper.
     *
     * @param requester the client requester used to send sampling requests
     * @param samplingManager the sampling manager
     */
    public CdiSampling(McpClientRequester requester, McpSamplingManager samplingManager) {
        this.requester = requester;
        this.samplingManager = samplingManager;
    }

    @Override
    public boolean isSupported() {
        return requester.supports("sampling");
    }

    @Override
    public SamplingRequest.Builder requestBuilder() {
        return new CdiSamplingRequest.CdiBuilder(samplingManager, requester);
    }
}
