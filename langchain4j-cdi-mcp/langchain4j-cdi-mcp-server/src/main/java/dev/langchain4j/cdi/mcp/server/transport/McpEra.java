package dev.langchain4j.cdi.mcp.server.transport;

/** Protocol era of a request: handshake-based (legacy) or per-request metadata (modern). */
public enum McpEra {
    LEGACY,
    MODERN
}
