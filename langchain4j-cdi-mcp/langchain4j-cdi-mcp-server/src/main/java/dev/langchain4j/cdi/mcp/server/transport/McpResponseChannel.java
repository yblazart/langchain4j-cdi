package dev.langchain4j.cdi.mcp.server.transport;

/** Destination for JSON-RPC messages related to a single client request. */
@FunctionalInterface
public interface McpResponseChannel {

    /**
     * Sends a JSON-RPC message (notification or final response).
     *
     * @param message a JSON-P value or any JSON-B serializable object
     */
    void send(Object message);

    /**
     * Returns whether the underlying transport is still usable.
     *
     * @return {@code false} once the underlying stream is known to be closed
     */
    default boolean isOpen() {
        return true;
    }
}
