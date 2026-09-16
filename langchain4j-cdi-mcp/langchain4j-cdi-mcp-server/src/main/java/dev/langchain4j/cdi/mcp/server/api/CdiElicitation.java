package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpElicitationManager;
import dev.langchain4j.cdi.mcp.server.transport.McpLegacyClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpSession;

/** Implementation of {@link Elicitation} that delegates to {@link McpElicitationManager}. */
public class CdiElicitation implements Elicitation {

    private final McpClientRequester requester;
    private final McpElicitationManager elicitationManager;

    /**
     * Creates a new elicitation wrapper bound to a legacy session.
     *
     * @param session the MCP session
     * @param elicitationManager the elicitation manager
     * @param sessionId the session identifier
     * @deprecated use {@link #CdiElicitation(McpClientRequester, McpElicitationManager)}
     */
    @Deprecated
    public CdiElicitation(McpSession session, McpElicitationManager elicitationManager, String sessionId) {
        this(new McpLegacyClientRequester(session, elicitationManager.getRequestManager()), elicitationManager);
    }

    /**
     * Creates a new elicitation wrapper.
     *
     * @param requester the client requester used to send elicitation requests
     * @param elicitationManager the elicitation manager
     */
    public CdiElicitation(McpClientRequester requester, McpElicitationManager elicitationManager) {
        this.requester = requester;
        this.elicitationManager = elicitationManager;
    }

    @Override
    public boolean isSupported() {
        return requester.supports("elicitation");
    }

    @Override
    public ElicitationRequest.Builder requestBuilder() {
        return new CdiElicitationRequest.CdiBuilder(elicitationManager, requester);
    }
}
