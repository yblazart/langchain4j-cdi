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
