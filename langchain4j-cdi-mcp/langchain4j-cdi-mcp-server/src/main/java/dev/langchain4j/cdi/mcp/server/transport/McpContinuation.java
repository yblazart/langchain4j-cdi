package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.protocol.McpJsonSerializer;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** A server method invocation suspended while waiting for client input (MRTR CONTINUATION mode). */
public final class McpContinuation {

    /** Event produced by the invocation for the handler. */
    public interface Event {}

    /** The invocation needs an answer from the client before it can proceed. */
    public record Input(McpInputRequiredSignal request) implements Event {}

    /** The invocation needs answers to every member of a batch before it can proceed (MRTR, SEP-2322). */
    public record InputBatch(List<McpInputRequiredSignal> requests) implements Event {}

    /** A single, not-yet-sent member of a batch of client interactions, awaited together via {@link #awaitBatch}. */
    public record PendingRequest(String key, String method, Map<String, Object> params) {}

    /** The invocation finished successfully. */
    public record Done(JsonObject result) implements Event {}

    /** The invocation raised an exception. */
    public record Failed(RuntimeException error) implements Event {}

    /**
     * Per-round request state: the transport the current round answers on, the progress token the current round's
     * client expects (or {@code null}), and whether the current round asked for log notifications.
     */
    private record Round(McpResponseChannel channel, Object progressToken, boolean logsRequested) {}

    private static final Round INITIAL_ROUND = new Round(McpNoopResponseChannel.INSTANCE, null, false);

    private final String id;
    private final Duration inputTimeout;
    private final Runnable onAbandon;
    private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
    private final Map<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final AtomicInteger next = new AtomicInteger();
    private final AtomicReference<Round> round = new AtomicReference<>(INITIAL_ROUND);
    private final AtomicBoolean cancelledFlag = new AtomicBoolean();
    private volatile McpInputRequiredSignal lastInput;
    private volatile List<McpInputRequiredSignal> lastInputs = List.of();

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
        return awaitInput(method, params, key);
    }

    /**
     * Called by the worker thread: blocks until the client answers this client request or {@code inputTimeout} elapses,
     * under the given key (MRTR, SEP-2322).
     *
     * @param method the JSON-RPC method sent to the client, e.g. {@code elicitation/create}
     * @param params the request params
     * @param key the key to emit the request under, or {@code null} to let the server assign one by call order
     * @return the client's answer
     */
    public JsonObject awaitInput(String method, Map<String, Object> params, String key) {
        String effectiveKey = key != null ? key : "input-" + next.getAndIncrement();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(effectiveKey, future);
        McpInputRequiredSignal input = new McpInputRequiredSignal(effectiveKey, method, params);
        lastInput = input;
        lastInputs = List.of(input);
        events.add(new Input(input));
        try {
            return future.get(inputTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (CancellationException e) {
            onAbandon.run();
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "Client input request cancelled");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onAbandon.run();
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "Interrupted while waiting for client input");
        } catch (ExecutionException | TimeoutException e) {
            onAbandon.run();
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "No client input received for " + method);
        } finally {
            pending.remove(effectiveKey);
        }
    }

    /**
     * Called by the worker thread: declares every member of a batch of client interactions before waiting on any of
     * them, and blocks until every member is answered or {@code inputTimeout} elapses (MRTR, SEP-2322). Every member is
     * registered as pending, and a single {@link InputBatch} event carrying all of them is emitted, before this method
     * blocks — so a retry can answer several of them in one round trip.
     *
     * <p>Each member is removed from the pending set the instant it is individually answered (or cancelled) — not only
     * once the whole batch resolves — so {@link #isWaitingFor(String)} is accurate for a batch exactly as it is for a
     * single {@link #awaitInput}. On timeout, cancellation or interruption this means members already answered report
     * {@link #isWaitingFor(String)} {@code false} and members still unanswered report {@code true}, even though this
     * call has already thrown.
     *
     * @param requests the batch members, in declaration order; must not be empty
     * @return every member's answer, keyed as declared
     */
    public Map<String, JsonObject> awaitBatch(List<PendingRequest> requests) {
        Map<String, CompletableFuture<JsonObject>> futures = new LinkedHashMap<>();
        List<McpInputRequiredSignal> signals = new ArrayList<>();
        for (PendingRequest r : requests) {
            String effectiveKey = r.key() != null ? r.key() : "input-" + next.getAndIncrement();
            CompletableFuture<JsonObject> future = new CompletableFuture<>();
            // removed the instant this one member is answered/cancelled, not only once the whole batch resolves
            future.whenComplete((value, error) -> pending.remove(effectiveKey, future));
            pending.put(effectiveKey, future);
            futures.put(effectiveKey, future);
            signals.add(new McpInputRequiredSignal(effectiveKey, r.method(), r.params()));
        }
        lastInputs = List.copyOf(signals);
        events.add(new InputBatch(lastInputs));
        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(inputTimeout.toMillis(), TimeUnit.MILLISECONDS);
            Map<String, JsonObject> result = new LinkedHashMap<>();
            futures.forEach((k, f) -> result.put(k, f.join()));
            return result;
        } catch (CancellationException e) {
            futures.values().forEach(f -> f.cancel(true));
            onAbandon.run();
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "Client input request cancelled");
        } catch (InterruptedException e) {
            futures.values().forEach(f -> f.cancel(true));
            Thread.currentThread().interrupt();
            onAbandon.run();
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "Interrupted while waiting for client input");
        } catch (ExecutionException | TimeoutException e) {
            futures.values().forEach(f -> f.cancel(true));
            onAbandon.run();
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "No client input received for batch");
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
        return supplyAndReport(inputResponses).size();
    }

    /**
     * Called by the handler: supplies client answers, unblocking the worker thread for each key that is currently
     * pending, and returns exactly which keys were unblocked. Unlike checking {@link #isWaitingFor(String)} afterward,
     * this is race-free: {@link CompletableFuture#complete} is synchronous, while the worker thread only removes a key
     * from the pending set (in a {@code finally} block) some time after it wakes up, so a caller that needs to know
     * precisely which keys this call answered — e.g. to decide which of several pending requests are still missing —
     * must use this return value, not a later {@link #isWaitingFor(String)} check.
     *
     * @param inputResponses the answers, keyed by input request key (e.g. {@code input-0})
     * @return the keys actually unblocked by this call
     */
    public Set<String> supplyAndReport(JsonObject inputResponses) {
        Set<String> supplied = new LinkedHashSet<>();
        for (Map.Entry<String, jakarta.json.JsonValue> entry : inputResponses.entrySet()) {
            CompletableFuture<JsonObject> future = pending.get(entry.getKey());
            if (future != null && entry.getValue() instanceof JsonObject response && future.complete(response)) {
                supplied.add(entry.getKey());
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
     * Returns the requests raised by the most recent {@link #awaitInput} (a singleton list) or {@link #awaitBatch} call
     * (one entry per batch member), whichever happened last.
     *
     * @return the most recent input request(s), or an empty list if none yet
     */
    public List<McpInputRequiredSignal> lastInputs() {
        return lastInputs;
    }

    /**
     * Sets the request-scoped state for the current round: the transport to deliver notifications on, the progress
     * token this round's client expects (or {@code null}), and whether this round asked for log notifications. Must be
     * called before the worker starts (round 1) and before supplying answers on every retry, so that request-scoped
     * notifications are never attributed to a stale round.
     *
     * @param channel the channel for this round, or {@code null} to drop notifications
     * @param progressToken the progress token this round's client expects, or {@code null} if none
     * @param logsRequested whether this round's client asked for log notifications
     */
    public void useRound(McpResponseChannel channel, Object progressToken, boolean logsRequested) {
        round.set(new Round(channel != null ? channel : McpNoopResponseChannel.INSTANCE, progressToken, logsRequested));
    }

    /**
     * Returns a channel delegating to the current round's channel. {@code notifications/progress} messages have their
     * {@code params.progressToken} rewritten to the current round's token (dropped if it has none);
     * {@code notifications/message} messages are dropped unless the current round asked for logs; anything else is
     * forwarded as-is. After forwarding, if the current round's channel reports it is no longer open, the
     * continuation-wide {@link #cancelledFlag()} is set.
     *
     * @return a channel delegating to the channel of the current round
     */
    public McpResponseChannel channel() {
        return this::deliver;
    }

    /**
     * Shared, continuation-wide cancellation flag: set once any round's channel is found closed. The worker thread uses
     * this flag (not any single round's local flag) as its {@code McpRequestContext.cancelledFlag()}, since the worker
     * outlives any one HTTP round.
     *
     * @return the continuation-wide cancellation flag
     */
    public AtomicBoolean cancelledFlag() {
        return cancelledFlag;
    }

    /** Cancels any pending client request, releasing the worker thread. */
    public void cancel() {
        pending.values().forEach(future -> future.cancel(true));
    }

    private void deliver(Object message) {
        Round current = round.get();
        JsonObject json = McpJsonSerializer.toJsonObject(message);
        String method = json.getString("method", "");
        Object toSend = message;
        if ("notifications/progress".equals(method)) {
            if (current.progressToken() == null) {
                return;
            }
            toSend = withProgressToken(json, current.progressToken());
        } else if ("notifications/message".equals(method)) {
            if (!current.logsRequested()) {
                return;
            }
        }
        current.channel().send(toSend);
        if (!current.channel().isOpen()) {
            cancelledFlag.set(true);
        }
    }

    private static JsonObject withProgressToken(JsonObject notification, Object token) {
        JsonObject params = notification.getJsonObject("params");
        JsonObjectBuilder params2 = params != null ? Json.createObjectBuilder(params) : Json.createObjectBuilder();
        params2.add("progressToken", McpJsonSerializer.toJsonValue(token));
        return Json.createObjectBuilder(notification).add("params", params2).build();
    }
}
