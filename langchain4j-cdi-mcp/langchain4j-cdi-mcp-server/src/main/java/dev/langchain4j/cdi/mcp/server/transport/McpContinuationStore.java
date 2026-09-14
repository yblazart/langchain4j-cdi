package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** In-memory registry of suspended invocations (MRTR CONTINUATION mode). */
@ApplicationScoped
public class McpContinuationStore {

    private final Map<String, McpContinuation> continuations = new ConcurrentHashMap<>();
    private final AtomicInteger threadCounter = new AtomicInteger();
    private volatile ExecutorService executor;

    @Inject
    McpMrtrSupport mrtr;

    /** CDI constructor. */
    public McpContinuationStore() {}

    /**
     * Creates a store backed by the given MRTR support (tests).
     *
     * @param mrtr the shared MRTR configuration and helpers
     */
    public McpContinuationStore(McpMrtrSupport mrtr) {
        this.mrtr = mrtr;
    }

    /**
     * Starts a new continuation, running {@code work} on a worker thread.
     *
     * @param work the invocation to run, given the continuation it can suspend on
     * @return the newly started continuation
     */
    public McpContinuation start(Function<McpContinuation, JsonObject> work) {
        String id = UUID.randomUUID().toString();
        McpContinuation continuation = new McpContinuation(id, mrtr.continuationTimeout(), () -> remove(id));
        continuations.put(id, continuation);
        executor().submit(() -> {
            try {
                continuation.complete(work.apply(continuation));
            } catch (RuntimeException e) {
                continuation.fail(e);
            }
        });
        return continuation;
    }

    /**
     * Looks up a continuation by id.
     *
     * @param id the continuation identifier, or {@code null}
     * @return the continuation, if still tracked
     */
    public Optional<McpContinuation> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(continuations.get(id));
    }

    /**
     * Stops tracking a continuation.
     *
     * @param id the continuation identifier, or {@code null}
     */
    public void remove(String id) {
        if (id != null) {
            continuations.remove(id);
        }
    }

    /** Cancels every tracked continuation and stops the worker thread pool. */
    @PreDestroy
    public void shutdown() {
        continuations.values().forEach(McpContinuation::cancel);
        continuations.clear();
        ExecutorService current = executor;
        if (current != null) {
            current.shutdownNow();
        }
    }

    private ExecutorService executor() {
        ExecutorService current = executor;
        if (current == null) {
            synchronized (this) {
                if (executor == null) {
                    executor = Executors.newCachedThreadPool(r -> {
                        Thread t = new Thread(r, "mcp-continuation-" + threadCounter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    });
                }
                current = executor;
            }
        }
        return current;
    }
}
