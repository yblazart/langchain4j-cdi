package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/** In-memory registry of suspended invocations (MRTR CONTINUATION mode). */
@ApplicationScoped
public class McpContinuationStore {

    private static final Logger LOGGER = Logger.getLogger(McpContinuationStore.class.getName());

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
     * Creates and tracks a new continuation, without starting its worker thread yet. Callers must set the
     * continuation's round-1 state via {@link McpContinuation#useRound} before calling {@link #run} so that early
     * notifications are not attributed to a stale (absent) round.
     *
     * @return the newly created continuation
     */
    public McpContinuation create() {
        String id = UUID.randomUUID().toString();
        McpContinuation continuation = new McpContinuation(id, mrtr.continuationTimeout(), () -> remove(id));
        continuations.put(id, continuation);
        return continuation;
    }

    /**
     * Runs {@code work} on a worker thread for a continuation previously returned by {@link #create()}. Any
     * {@link RuntimeException} raised by {@code work} is delivered as a {@link McpContinuation.Failed} event; any other
     * {@link Throwable} (an {@link Error}, or a sneaky-thrown checked exception) is wrapped into an
     * {@link McpException} and delivered the same way, so the handler is never left waiting for an event that will
     * never come. Failures that are not already an {@link McpException} are logged at {@code WARNING}.
     *
     * @param continuation the continuation to run the work under
     * @param work the invocation to run, given the continuation it can suspend on
     */
    public void run(McpContinuation continuation, Function<McpContinuation, JsonObject> work) {
        executor().submit(() -> {
            try {
                continuation.complete(work.apply(continuation));
            } catch (RuntimeException e) {
                logIfUnexpected(e);
                continuation.fail(e);
            } catch (Throwable t) {
                logIfUnexpected(t);
                continuation.fail(new McpException(null, McpErrorCode.INTERNAL_ERROR, "Invocation failed: " + t));
            }
        });
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

    private static void logIfUnexpected(Throwable t) {
        if (!(t instanceof McpException)) {
            LOGGER.log(Level.WARNING, "MCP: continuation worker failed", t);
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
