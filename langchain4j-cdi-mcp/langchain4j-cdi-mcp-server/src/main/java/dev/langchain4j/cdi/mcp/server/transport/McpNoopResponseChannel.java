package dev.langchain4j.cdi.mcp.server.transport;

/** Drops messages: used for modern requests answered with a single JSON object. */
public enum McpNoopResponseChannel implements McpResponseChannel {
    /** The single instance of this no-op channel. */
    INSTANCE;

    @Override
    public void send(Object message) {
        // no stream to write request-scoped notifications to
    }
}
