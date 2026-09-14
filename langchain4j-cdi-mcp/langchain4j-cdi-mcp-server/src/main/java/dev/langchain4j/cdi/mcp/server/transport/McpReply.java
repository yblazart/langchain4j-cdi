package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Transport-neutral HTTP reply produced by protocol handlers, converted to a JAX-RS response by {@link McpEndpoint}.
 *
 * @param status HTTP status
 * @param contentType content type, {@code null} when there is no body
 * @param body JSON body, {@code null} for streams and 202
 * @param stream SSE writer, {@code null} for non-streaming replies
 */
public record McpReply(int status, String contentType, String body, StreamWriter stream) {

    /** Writes an SSE response body. */
    @FunctionalInterface
    public interface StreamWriter {
        void write(OutputStream out) throws IOException;
    }

    /**
     * Creates a JSON reply.
     *
     * @param status the HTTP status
     * @param body the JSON body
     * @return the reply
     */
    public static McpReply json(int status, JsonObject body) {
        return new McpReply(status, "application/json", body.toString(), null);
    }

    /**
     * Creates an SSE reply.
     *
     * @param writer writes the SSE event stream
     * @return the reply
     */
    public static McpReply sse(StreamWriter writer) {
        return new McpReply(200, "text/event-stream", null, writer);
    }

    /**
     * Creates a {@code 202 Accepted} reply with no body, used for modern notifications.
     *
     * @return the reply
     */
    public static McpReply accepted() {
        return new McpReply(202, null, null, null);
    }

    /**
     * Returns whether this reply is a streaming (SSE) reply.
     *
     * @return {@code true} if this reply carries a {@link StreamWriter}
     */
    public boolean isStream() {
        return stream != null;
    }
}
