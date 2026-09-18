package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import dev.langchain4j.cdi.mcp.server.logging.McpLogger;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcNotification;
import dev.langchain4j.cdi.mcp.server.protocol.McpJsonSerializer;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class McpContinuationTest {

    @Test
    void workerWaitsUntilInputIsSupplied() throws Exception {
        McpContinuation continuation = new McpContinuation("c1", Duration.ofSeconds(5), () -> {});
        CompletableFuture<JsonObject> worker = CompletableFuture.supplyAsync(
                () -> continuation.awaitInput("elicitation/create", Map.of("message", "Name?")));

        McpContinuation.Event event = continuation.nextEvent(Duration.ofSeconds(5));
        assertThat(event).isInstanceOf(McpContinuation.Input.class);
        String key = ((McpContinuation.Input) event).request().key();
        assertThat(key).isEqualTo("input-0");
        assertThat(continuation.isWaitingFor(key)).isTrue();
        assertThat(continuation.lastInput().method()).isEqualTo("elicitation/create");

        JsonObject answer = Json.createObjectBuilder().add("action", "accept").build();
        assertThat(continuation.supply(Json.createObjectBuilder()
                        .add(key, answer)
                        .add("unknown", answer)
                        .build()))
                .isEqualTo(1);

        assertThat(worker.get(5, TimeUnit.SECONDS)).isEqualTo(answer);
        assertThat(continuation.isWaitingFor(key)).isFalse();
    }

    @Test
    void explicitKeyIsUsedVerbatim() throws Exception {
        McpContinuation continuation = new McpContinuation("k1", Duration.ofSeconds(5), () -> {});
        CompletableFuture<JsonObject> worker = CompletableFuture.supplyAsync(
                () -> continuation.awaitInput("elicitation/create", Map.of("message", "Name?"), "user_name"));

        McpContinuation.Event event = continuation.nextEvent(Duration.ofSeconds(5));
        assertThat(event).isInstanceOf(McpContinuation.Input.class);
        String key = ((McpContinuation.Input) event).request().key();
        assertThat(key).isEqualTo("user_name");
        assertThat(continuation.isWaitingFor("user_name")).isTrue();

        JsonObject answer = Json.createObjectBuilder().add("action", "accept").build();
        assertThat(continuation.supply(
                        Json.createObjectBuilder().add("user_name", answer).build()))
                .isEqualTo(1);
        assertThat(worker.get(5, TimeUnit.SECONDS)).isEqualTo(answer);
    }

    @Test
    void nullKeyFallsBackToAutoNumbering() throws Exception {
        McpContinuation continuation = new McpContinuation("k2", Duration.ofSeconds(5), () -> {});
        CompletableFuture<JsonObject> worker =
                CompletableFuture.supplyAsync(() -> continuation.awaitInput("elicitation/create", Map.of(), null));

        McpContinuation.Event event = continuation.nextEvent(Duration.ofSeconds(5));
        String key = ((McpContinuation.Input) event).request().key();
        assertThat(key).isEqualTo("input-0");

        JsonObject answer = Json.createObjectBuilder().add("action", "accept").build();
        continuation.supply(Json.createObjectBuilder().add(key, answer).build());
        assertThat(worker.get(5, TimeUnit.SECONDS)).isEqualTo(answer);
    }

    @Test
    void batchAnsweredInASingleRetry() throws Exception {
        McpContinuation continuation = new McpContinuation("b1", Duration.ofSeconds(5), () -> {});
        List<McpContinuation.PendingRequest> requests = List.of(
                new McpContinuation.PendingRequest("user_name", "elicitation/create", Map.of()),
                new McpContinuation.PendingRequest("summary", "sampling/createMessage", Map.of()),
                new McpContinuation.PendingRequest("roots", "roots/list", Map.of()));
        CompletableFuture<Map<String, JsonObject>> worker =
                CompletableFuture.supplyAsync(() -> continuation.awaitBatch(requests));

        McpContinuation.Event event = continuation.nextEvent(Duration.ofSeconds(5));
        assertThat(event).isInstanceOf(McpContinuation.InputBatch.class);
        List<McpInputRequiredSignal> pending = ((McpContinuation.InputBatch) event).requests();
        assertThat(pending).extracting(McpInputRequiredSignal::key).containsExactly("user_name", "summary", "roots");
        assertThat(continuation.isWaitingFor("user_name")).isTrue();
        assertThat(continuation.isWaitingFor("summary")).isTrue();
        assertThat(continuation.isWaitingFor("roots")).isTrue();

        JsonObject answer = Json.createObjectBuilder().add("action", "accept").build();
        int supplied = continuation.supply(Json.createObjectBuilder()
                .add("user_name", answer)
                .add("summary", answer)
                .add("roots", answer)
                .build());
        assertThat(supplied).isEqualTo(3);

        Map<String, JsonObject> result = worker.get(5, TimeUnit.SECONDS);
        assertThat(result).containsOnlyKeys("user_name", "summary", "roots");
        assertThat(continuation.isWaitingFor("user_name")).isFalse();
        assertThat(continuation.isWaitingFor("summary")).isFalse();
        assertThat(continuation.isWaitingFor("roots")).isFalse();
    }

    @Test
    void batchAnsweredAcrossTwoRetriesPartialAnswersFirst() throws Exception {
        McpContinuation continuation = new McpContinuation("b2", Duration.ofSeconds(5), () -> {});
        List<McpContinuation.PendingRequest> requests = List.of(
                new McpContinuation.PendingRequest("user_name", "elicitation/create", Map.of()),
                new McpContinuation.PendingRequest("summary", "sampling/createMessage", Map.of()),
                new McpContinuation.PendingRequest("roots", "roots/list", Map.of()));
        CompletableFuture<Map<String, JsonObject>> worker =
                CompletableFuture.supplyAsync(() -> continuation.awaitBatch(requests));
        continuation.nextEvent(Duration.ofSeconds(5));

        JsonObject answer = Json.createObjectBuilder().add("action", "accept").build();
        // first retry only answers one of the three
        assertThat(continuation.supply(
                        Json.createObjectBuilder().add("user_name", answer).build()))
                .isEqualTo(1);
        assertThat(worker.isDone()).isFalse();
        assertThat(continuation.isWaitingFor("user_name")).isFalse();
        assertThat(continuation.isWaitingFor("summary")).isTrue();
        assertThat(continuation.isWaitingFor("roots")).isTrue();

        // second retry answers the remaining two
        assertThat(continuation.supply(Json.createObjectBuilder()
                        .add("summary", answer)
                        .add("roots", answer)
                        .build()))
                .isEqualTo(2);

        Map<String, JsonObject> result = worker.get(5, TimeUnit.SECONDS);
        assertThat(result).containsOnlyKeys("user_name", "summary", "roots");
    }

    @Test
    void batchTimesOutWithSomeKeysStillPending() throws Exception {
        McpContinuation continuation = new McpContinuation("b3", Duration.ofMillis(200), () -> {});
        List<McpContinuation.PendingRequest> requests = List.of(
                new McpContinuation.PendingRequest("user_name", "elicitation/create", Map.of()),
                new McpContinuation.PendingRequest("roots", "roots/list", Map.of()));
        CompletableFuture<Map<String, JsonObject>> worker =
                CompletableFuture.supplyAsync(() -> continuation.awaitBatch(requests));
        continuation.nextEvent(Duration.ofSeconds(5));

        JsonObject answer = Json.createObjectBuilder().add("action", "accept").build();
        continuation.supply(Json.createObjectBuilder().add("user_name", answer).build());

        assertThatThrownBy(() -> worker.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(McpException.class);

        assertThat(continuation.isWaitingFor("user_name")).isFalse();
        // after timeout, unanswered futures are cancelled and removed from the pending map
        assertThat(continuation.isWaitingFor("roots")).isFalse();
    }

    @Test
    void twoConcurrentBatchesDoNotLeakPendingKeysIntoEachOther() throws Exception {
        McpContinuation c1 = new McpContinuation("conc1", Duration.ofSeconds(5), () -> {});
        McpContinuation c2 = new McpContinuation("conc2", Duration.ofSeconds(5), () -> {});
        List<McpContinuation.PendingRequest> requests = List.of(
                new McpContinuation.PendingRequest("user_name", "elicitation/create", Map.of()),
                new McpContinuation.PendingRequest("summary", "sampling/createMessage", Map.of()));

        CompletableFuture<Map<String, JsonObject>> worker1 =
                CompletableFuture.supplyAsync(() -> c1.awaitBatch(requests));
        CompletableFuture<Map<String, JsonObject>> worker2 =
                CompletableFuture.supplyAsync(() -> c2.awaitBatch(requests));
        c1.nextEvent(Duration.ofSeconds(5));
        c2.nextEvent(Duration.ofSeconds(5));

        JsonObject answerC1 = Json.createObjectBuilder()
                .add("action", "accept")
                .add("who", "c1")
                .build();
        JsonObject answerC2 = Json.createObjectBuilder()
                .add("action", "accept")
                .add("who", "c2")
                .build();

        // answer c1's batch only; c2's identically-keyed batch must be unaffected
        c1.supply(Json.createObjectBuilder()
                .add("user_name", answerC1)
                .add("summary", answerC1)
                .build());
        Map<String, JsonObject> result1 = worker1.get(5, TimeUnit.SECONDS);
        assertThat(result1.get("user_name")).isEqualTo(answerC1);
        assertThat(c2.isWaitingFor("user_name")).isTrue();
        assertThat(c2.isWaitingFor("summary")).isTrue();
        assertThat(worker2.isDone()).isFalse();

        c2.supply(Json.createObjectBuilder()
                .add("user_name", answerC2)
                .add("summary", answerC2)
                .build());
        Map<String, JsonObject> result2 = worker2.get(5, TimeUnit.SECONDS);
        assertThat(result2.get("user_name")).isEqualTo(answerC2);
        assertThat(c1.isWaitingFor("user_name")).isFalse();
        assertThat(c2.isWaitingFor("user_name")).isFalse();
    }

    @Test
    void completionAndFailureAreDeliveredAsEvents() throws Exception {
        McpContinuation continuation = new McpContinuation("c2", Duration.ofSeconds(5), () -> {});

        continuation.complete(Json.createObjectBuilder().add("ok", true).build());
        continuation.fail(new IllegalStateException("boom"));

        assertThat(continuation.nextEvent(Duration.ofSeconds(1))).isInstanceOf(McpContinuation.Done.class);
        assertThat(((McpContinuation.Failed) continuation.nextEvent(Duration.ofSeconds(1))).error())
                .hasMessage("boom");
        assertThat(continuation.nextEvent(Duration.ofMillis(10))).isNull();
    }

    @Test
    void unansweredInputTimesOutAndAbandons() {
        AtomicBoolean abandoned = new AtomicBoolean();
        McpContinuation continuation = new McpContinuation("c3", Duration.ofMillis(50), () -> abandoned.set(true));

        assertThatThrownBy(() -> continuation.awaitInput("roots/list", Map.of()))
                .isInstanceOf(McpException.class);
        assertThat(abandoned).isTrue();
    }

    @Test
    void cancelWhileWaitingForInputYieldsCancelledException() throws Exception {
        AtomicBoolean abandoned = new AtomicBoolean();
        McpContinuation continuation = new McpContinuation("c3b", Duration.ofSeconds(5), () -> abandoned.set(true));
        CompletableFuture<JsonObject> worker =
                CompletableFuture.supplyAsync(() -> continuation.awaitInput("roots/list", Map.of()));

        // consume the Input event: by the time it is queued, the pending future is already registered
        assertThat(continuation.nextEvent(Duration.ofSeconds(5))).isInstanceOf(McpContinuation.Input.class);
        continuation.cancel();

        assertThatThrownBy(() -> worker.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(McpException.class);
        assertThat(abandoned).isTrue();
    }

    @Test
    void throwableFromWorkerBecomesFailedEventPromptly() throws Exception {
        McpMrtrSupport support = new McpMrtrSupport(
                new McpServerConfigResolver(McpServerConfig.builder().build()));
        McpContinuationStore store = new McpContinuationStore(support);
        try {
            McpContinuation continuation = store.create();
            continuation.useRound(McpNoopResponseChannel.INSTANCE, null, false);
            store.run(continuation, started -> {
                throw new AssertionError("boom");
            });

            McpContinuation.Event event = continuation.nextEvent(Duration.ofSeconds(5));

            assertThat(event).isInstanceOf(McpContinuation.Failed.class);
            RuntimeException error = ((McpContinuation.Failed) event).error();
            assertThat(error).isInstanceOf(McpException.class);
            assertThat(error.getMessage()).contains("boom");
        } finally {
            store.shutdown();
        }
    }

    @Test
    void channelCanBeSwitchedBetweenRounds() {
        McpContinuation continuation = new McpContinuation("c4", Duration.ofSeconds(1), () -> {});
        List<Object> first = new ArrayList<>();
        List<Object> second = new ArrayList<>();

        continuation.useRound(first::add, null, false);
        continuation.channel().send(JsonRpcNotification.toolsListChanged());
        continuation.useRound(second::add, null, false);
        continuation.channel().send(JsonRpcNotification.toolsListChanged());

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
    }

    @Test
    void progressTokenIsRewrittenToTheCurrentRound() {
        McpContinuation continuation = new McpContinuation("c5", Duration.ofSeconds(1), () -> {});
        List<Object> secondRound = new ArrayList<>();

        continuation.useRound(m -> {}, "round1-token", false);
        continuation.useRound(secondRound::add, "round2-token", false);
        continuation.channel().send(JsonRpcNotification.progress("round1-token", 1, 2, null));

        assertThat(secondRound).hasSize(1);
        JsonObject delivered = McpJsonSerializer.toJsonObject(secondRound.get(0));
        assertThat(delivered.getJsonObject("params").getString("progressToken")).isEqualTo("round2-token");
    }

    @Test
    void progressTokenLookingLikeJsonIsRewrittenAsString() {
        McpContinuation continuation = new McpContinuation("c5b", Duration.ofSeconds(1), () -> {});
        List<Object> messages = new ArrayList<>();

        continuation.useRound(messages::add, "{x", false);
        continuation.channel().send(JsonRpcNotification.progress("round1-token", 1, 2, null));

        assertThat(messages).hasSize(1);
        JsonObject delivered = McpJsonSerializer.toJsonObject(messages.get(0));
        assertThat(delivered.getJsonObject("params").getString("progressToken")).isEqualTo("{x");
    }

    @Test
    void progressIsDroppedWhenCurrentRoundHasNoToken() {
        McpContinuation continuation = new McpContinuation("c6", Duration.ofSeconds(1), () -> {});
        List<Object> messages = new ArrayList<>();
        continuation.useRound(messages::add, null, false);

        continuation.channel().send(JsonRpcNotification.progress("round1-token", 1, 2, null));

        assertThat(messages).isEmpty();
    }

    @Test
    void logNotificationIsDroppedUnlessCurrentRoundRequestedLogs() {
        McpContinuation continuation = new McpContinuation("c7", Duration.ofSeconds(1), () -> {});
        List<Object> messages = new ArrayList<>();
        Map<String, Object> logNotification = McpLogger.notification(McpLogLevel.info, "test", "hello");

        continuation.useRound(messages::add, null, false);
        continuation.channel().send(logNotification);
        assertThat(messages).isEmpty();

        continuation.useRound(messages::add, null, true);
        continuation.channel().send(logNotification);
        assertThat(messages).hasSize(1);
    }

    @Test
    void closedRoundChannelSetsTheContinuationWideCancelledFlag() {
        McpContinuation continuation = new McpContinuation("c8", Duration.ofSeconds(1), () -> {});
        AtomicBoolean open = new AtomicBoolean(true);
        McpResponseChannel closingChannel = new McpResponseChannel() {
            @Override
            public void send(Object message) {}

            @Override
            public boolean isOpen() {
                return open.get();
            }
        };

        continuation.useRound(closingChannel, null, false);
        continuation.channel().send(JsonRpcNotification.toolsListChanged());
        assertThat(continuation.cancelledFlag()).isFalse();

        open.set(false);
        continuation.channel().send(JsonRpcNotification.toolsListChanged());
        assertThat(continuation.cancelledFlag()).isTrue();
    }
}
