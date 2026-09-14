package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

/** Recording {@link SseEventSink} that renders events with the standard SSE wire framing. */
class FakeSseEventSink implements SseEventSink {

    private final List<OutboundSseEvent> events = new CopyOnWriteArrayList<>();
    private volatile boolean closed;
    private volatile Throwable asyncFailure;
    private volatile RuntimeException syncFailure;

    /** Makes subsequent sends complete exceptionally. */
    void failSends(Throwable failure) {
        this.asyncFailure = failure;
    }

    /** Makes subsequent sends throw synchronously. */
    void throwOnSend(RuntimeException failure) {
        this.syncFailure = failure;
    }

    List<OutboundSseEvent> events() {
        return events;
    }

    String rendered() {
        StringBuilder sb = new StringBuilder();
        for (OutboundSseEvent event : events) {
            if (event.getComment() != null) {
                sb.append(": ").append(event.getComment()).append('\n');
            }
            if (event.getName() != null) {
                sb.append("event: ").append(event.getName()).append('\n');
            }
            if (event.getData() != null) {
                sb.append("data: ").append(event.getData()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public CompletionStage<?> send(OutboundSseEvent event) {
        if (closed) {
            throw new IllegalStateException("Already closed");
        }
        if (syncFailure != null) {
            throw syncFailure;
        }
        if (asyncFailure != null) {
            return CompletableFuture.failedFuture(asyncFailure);
        }
        events.add(event);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        closed = true;
    }
}
