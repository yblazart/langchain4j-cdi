package dev.langchain4j.cdi.mcp.server.transport;

/** Strategy used to serve client interactions (elicitation, sampling, roots) with MCP 2026-07-28 clients. */
public enum McpMrtrMode {
    /** Stateless: the method is re-executed on each retry; collected answers travel in a signed requestState. */
    REPLAY,
    /** Stateful: the invocation thread waits for the answer; requires sticky routing in a cluster. */
    CONTINUATION
}
