package dev.langchain4j.cdi.mcp.integrationtests;

import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.ext.Provider;

/**
 * Disables Jersey's outbound content-length auto-detection buffering. This is a portable JAX-RS {@link Feature},
 * inert on non-Jersey JAX-RS implementations, needed so long-lived SSE streams such as {@code subscriptions/listen}
 * are flushed to the client as soon as the server writes to them, instead of being held in an internal buffer (8 KB
 * by default) until the response closes or the buffer fills up.
 *
 * <p>Without this, a client blocks indefinitely waiting for the first SSE event on Jersey-based test runtimes (e.g.
 * Helidon MP): Jersey's {@code CommittingOutputStream} only commits (and actually writes to the socket) once the
 * buffered content exceeds {@code jersey.config.server.contentLength.buffer} bytes or the stream is closed — an
 * {@code out.flush()} call on the application side is a no-op while the stream is still buffering, since a
 * {@code subscriptions/listen} stream is deliberately kept open past the first message.
 */
@Provider
public class McpTestJerseyBufferingFeature implements Feature {

    /** Creates a new instance. */
    public McpTestJerseyBufferingFeature() {}

    @Override
    public boolean configure(FeatureContext context) {
        context.property("jersey.config.server.contentLength.buffer", 0);
        return true;
    }
}
