package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    void channelCanBeSwitchedBetweenRounds() {
        McpContinuation continuation = new McpContinuation("c4", Duration.ofSeconds(1), () -> {});
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();

        continuation.useChannel(first::append);
        continuation.channel().send("a");
        continuation.useChannel(second::append);
        continuation.channel().send("b");

        assertThat(first).hasToString("a");
        assertThat(second).hasToString("b");
    }
}
