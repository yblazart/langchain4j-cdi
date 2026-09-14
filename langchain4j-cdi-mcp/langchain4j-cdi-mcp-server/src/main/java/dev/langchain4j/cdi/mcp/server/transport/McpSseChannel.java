package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.protocol.McpJsonSerializer;

/**
 * Server-sent events stream of one HTTP response, carrying JSON-RPC messages as {@code message} events. Implementations
 * adapt a concrete transport (a raw output stream or a Jakarta REST {@code SseEventSink}) so protocol handlers stay
 * transport-neutral.
 */
public interface McpSseChannel extends McpResponseChannel {

    /**
     * Sends one {@code message} event carrying the given single-line JSON text as its data.
     *
     * @param json the serialized JSON-RPC message
     */
    void sendData(String json);

    /**
     * Sends an SSE comment, typically used as keep-alive.
     *
     * @param comment the comment text
     */
    void sendComment(String comment);

    /**
     * Serializes a JSON-RPC message and sends it as a {@code message} event.
     *
     * @param message a JSON-P value or any JSON-B serializable object
     */
    @Override
    default void send(Object message) {
        sendData(McpJsonSerializer.toJsonValue(message).toString());
    }

    /** Closes the stream, ending the HTTP response where the transport allows it. Closing twice has no effect. */
    void close();
}
