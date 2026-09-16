package dev.langchain4j.cdi.mcp.server.api;

import dev.langchain4j.cdi.mcp.server.protocol.McpRoot;
import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpLegacyClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpRootsManager;
import dev.langchain4j.cdi.mcp.server.transport.McpSession;
import java.util.List;

/** Implementation of {@link Roots} that delegates to {@link McpRootsManager}. */
public class CdiRoots implements Roots {

    private final McpClientRequester requester;
    private final McpRootsManager rootsManager;

    /**
     * Creates a new roots wrapper bound to a legacy session.
     *
     * @param session the MCP session
     * @param rootsManager the roots manager
     * @param sessionId the session identifier
     * @deprecated use {@link #CdiRoots(McpClientRequester, McpRootsManager)}
     */
    @Deprecated
    public CdiRoots(McpSession session, McpRootsManager rootsManager, String sessionId) {
        this(new McpLegacyClientRequester(session, rootsManager.getRequestManager()), rootsManager);
    }

    /**
     * Creates a new roots wrapper.
     *
     * @param requester the client requester used to send roots requests
     * @param rootsManager the roots manager
     */
    public CdiRoots(McpClientRequester requester, McpRootsManager rootsManager) {
        this.requester = requester;
        this.rootsManager = rootsManager;
    }

    @Override
    public boolean isSupported() {
        return requester.supports("roots");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T list() {
        return (T) listAndAwait();
    }

    @Override
    public List<McpRoot> listAndAwait() {
        requester.requireCapability("roots");
        return rootsManager.requestRoots(requester);
    }

    @Override
    public List<McpRoot> listAndAwait(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Roots.listAndAwait: key must not be blank");
        }
        requester.requireCapability("roots");
        return rootsManager.requestRoots(requester, key);
    }
}
