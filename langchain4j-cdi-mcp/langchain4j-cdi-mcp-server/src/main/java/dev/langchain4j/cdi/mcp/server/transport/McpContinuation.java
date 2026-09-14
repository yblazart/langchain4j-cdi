package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** A server method invocation suspended while waiting for client input (MRTR CONTINUATION mode). */
public final class McpContinuation {

    /** Event produced by the invocation for the handler. */
    public interface Event {}

    /** The invocation needs an answer from the client before it can proceed. */
    public record Input(McpInputRequiredSignal request) implements Event {}

    /** The invocation finished successfully. */
    public record Done(JsonObject result) implements Event {}

    /** The invocation raised an exception. */
    public record Failed(RuntimeException error) implements Event {}

    private final String id;
    private final Duration inputTimeout;
    private final Runnable onAbandon;
    private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
    private final Map<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final AtomicInteger next = new AtomicInteger();
    private final AtomicReference<McpResponseChannel> channel = new AtomicReference<>(McpNoopResponseChannel.INSTANCE);
    private volatile McpInputRequiredSignal lastInput;

    /**
     * Creates a continuation.
     *
     * @param id the continuation identifier, carried by the signed {@code requestState}
     * @param inputTimeout the maximum time to wait for a client answer before abandoning the invocation
     * @param onAbandon called when a client answer does not arrive within {@code inputTimeout}
     */
    public McpContinuation(String id, Duration inputTimeout, Runnable onAbandon) {
        this.id = id;
        this.inputTimeout = inputTimeout;
        this.onAbandon = onAbandon;
    }

    /** @return the continuation identifier */
    public String id() {
        return id;
    }

    /**
     * Called by the worker thread: blocks until the client answers this client request or {@code inputTimeout} elapses.
     *
     * @param method the JSON-RPC method sent to the client, e.g. {@code elicitation/create}
     * @param params the request params
     * @return the client's answer
     */
    public JsonObject awaitInput(String method, Map<String, Object> params) {
        String key = "input-" + next.getAndIncrement();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(key, future);
        McpInputRequiredSignal input = new McpInputRequiredSignal(key, method, params);
        lastInput = input;
        events.add(new Input(input));
        try {
            return future.get(inputTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onAbandon.run();
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "Interrupted while waiting for client input");
        } catch (ExecutionException | TimeoutException e) {
            onAbandon.run();
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "No client input received for " + method);
        } finally {
            pending.remove(key);
        }
    }

    /**
     * Called by the worker thread: delivers the invocation's final result.
     *
     * @param result the invocation result
     */
    public void complete(JsonObject result) {
        events.add(new Done(result));
    }

    /**
     * Called by the worker thread: delivers the invocation's failure.
     *
     * @param error the exception raised by the invocation
     */
    public void fail(RuntimeException error) {
        events.add(new Failed(error));
    }

    /**
     * Called by the handler: waits for the next event (new input request, result, or failure).
     *
     * @param timeout the maximum time to wait
     * @return the next event, or {@code null} if none arrived within {@code timeout}
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public Event nextEvent(Duration timeout) throws InterruptedException {
        return events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Called by the handler: supplies client answers, unblocking the worker thread for each key that is currently
     * pending.
     *
     * @param inputResponses the answers, keyed by input request key (e.g. {@code input-0})
     * @return the number of pending requests actually unblocked
     */
    public int supply(JsonObject inputResponses) {
        int supplied = 0;
        for (Map.Entry<String, jakarta.json.JsonValue> entry : inputResponses.entrySet()) {
            CompletableFuture<JsonObject> future = pending.get(entry.getKey());
            if (future != null && entry.getValue() instanceof JsonObject response && future.complete(response)) {
                supplied++;
            }
        }
        return supplied;
    }

    /**
     * Returns whether the invocation is currently blocked waiting for an answer to the given key.
     *
     * @param key the input request key
     * @return {@code true} if the worker thread is still waiting for this key
     */
    public boolean isWaitingFor(String key) {
        return pending.containsKey(key);
    }

    /** @return the most recent input request raised by the invocation, or {@code null} if none yet */
    public McpInputRequiredSignal lastInput() {
        return lastInput;
    }

    /**
     * Sets the channel used to deliver request-scoped notifications (progress, logs) for the current round.
     *
     * @param responseChannel the channel for this round, or {@code null} to drop notifications
     */
    public void useChannel(McpResponseChannel responseChannel) {
        channel.set(responseChannel != null ? responseChannel : McpNoopResponseChannel.INSTANCE);
    }

    /** @return a channel delegating to the channel of the current round */
    public McpResponseChannel channel() {
        return message -> channel.get().send(message);
    }

    /** Cancels any pending client request, releasing the worker thread. */
    public void cancel() {
        pending.values().forEach(future -> future.cancel(true));
    }
}
