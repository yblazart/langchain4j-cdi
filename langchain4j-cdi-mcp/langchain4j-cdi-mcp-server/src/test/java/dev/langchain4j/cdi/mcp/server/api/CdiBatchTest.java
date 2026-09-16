package dev.langchain4j.cdi.mcp.server.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.protocol.McpSamplingMessage;
import dev.langchain4j.cdi.mcp.server.transport.McpElicitationManager;
import dev.langchain4j.cdi.mcp.server.transport.McpEra;
import dev.langchain4j.cdi.mcp.server.transport.McpInputRequiredBatchSignal;
import dev.langchain4j.cdi.mcp.server.transport.McpProtocolContext;
import dev.langchain4j.cdi.mcp.server.transport.McpReplayClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpSamplingManager;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CdiBatchTest {

    static final McpProtocolContext ALL_CAPS = new McpProtocolContext(
            McpEra.MODERN,
            "2026-07-28",
            Json.createObjectBuilder()
                    .add("elicitation", JsonValue.EMPTY_JSON_OBJECT)
                    .add("sampling", JsonValue.EMPTY_JSON_OBJECT)
                    .add("roots", JsonValue.EMPTY_JSON_OBJECT)
                    .build(),
            null,
            null);

    static final McpProtocolContext ELICITATION_ONLY = new McpProtocolContext(
            McpEra.MODERN,
            "2026-07-28",
            Json.createObjectBuilder()
                    .add("elicitation", JsonValue.EMPTY_JSON_OBJECT)
                    .build(),
            null,
            null);

    private static ElicitationRequest elicitationRequest(McpProtocolContext protocol, String key) {
        Elicitation elicitation =
                new CdiElicitation(new McpReplayClientRequester(protocol, Map.of()), new McpElicitationManager());
        var builder = elicitation.requestBuilder().setMessage("Your name?");
        if (key != null) {
            builder.setKey(key);
        }
        return builder.build();
    }

    private static SamplingRequest samplingRequest(McpProtocolContext protocol) {
        Sampling sampling = new CdiSampling(new McpReplayClientRequester(protocol, Map.of()), new McpSamplingManager());
        return sampling.requestBuilder()
                .addMessage(new McpSamplingMessage("user", Map.of("type", "text", "text", "hi")))
                .setMaxTokens(16)
                .build();
    }

    @Test
    void emptyBatchIsRejected() {
        McpInteractions interactions = new CdiInteractions(new McpReplayClientRequester(ALL_CAPS, Map.of()));

        assertThatThrownBy(() -> interactions.batch().awaitAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void duplicateKeyWithinABatchIsRejected() {
        McpInteractions interactions = new CdiInteractions(new McpReplayClientRequester(ALL_CAPS, Map.of()));

        assertThatThrownBy(() -> interactions
                        .batch()
                        .elicit("dup", elicitationRequest(ALL_CAPS, null))
                        .roots("dup"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate key");
    }

    @Test
    void requestOwnKeyConflictingWithBatchKeyIsRejected() {
        McpInteractions interactions = new CdiInteractions(new McpReplayClientRequester(ALL_CAPS, Map.of()));

        assertThatThrownBy(() -> interactions.batch().elicit("batch_key", elicitationRequest(ALL_CAPS, "own_key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch key");
    }

    @Test
    void requestOwnKeyEqualToBatchKeyIsAccepted() {
        McpInteractions interactions = new CdiInteractions(new McpReplayClientRequester(ALL_CAPS, Map.of()));

        assertThatThrownBy(() -> interactions
                        .batch()
                        .elicit("same_key", elicitationRequest(ALL_CAPS, "same_key"))
                        .awaitAll())
                .isInstanceOf(McpInputRequiredBatchSignal.class);
    }

    @Test
    void missingClientCapabilityFailsTheWholeBatchUpFront() {
        McpInteractions interactions = new CdiInteractions(new McpReplayClientRequester(ELICITATION_ONLY, Map.of()));

        assertThatThrownBy(() -> interactions
                        .batch()
                        .elicit("user_name", elicitationRequest(ELICITATION_ONLY, null))
                        .sample("summary", samplingRequest(ELICITATION_ONLY))
                        .awaitAll())
                .isInstanceOf(McpException.class);
    }

    @Test
    void replayReportsEveryMissingRequestAtOnceNeverJustTheFirst() {
        McpInteractions interactions = new CdiInteractions(new McpReplayClientRequester(ALL_CAPS, Map.of()));

        assertThatThrownBy(() -> interactions
                        .batch()
                        .elicit("user_name", elicitationRequest(ALL_CAPS, null))
                        .sample("summary", samplingRequest(ALL_CAPS))
                        .roots("roots")
                        .awaitAll())
                .isInstanceOfSatisfying(McpInputRequiredBatchSignal.class, signal -> {
                    assertThat(signal.missing()).hasSize(3);
                    assertThat(signal.missing().stream().map(s -> s.key()).toList())
                            .containsExactly("user_name", "summary", "roots");
                    assertThat(signal.missing().stream().map(s -> s.method()).toList())
                            .containsExactly("elicitation/create", "sampling/createMessage", "roots/list");
                });
    }

    @Test
    void replayAnswersAlreadyKnownKeysAndSkipsThem() {
        JsonObject answer = Json.createObjectBuilder()
                .add("action", "accept")
                .add("content", Json.createObjectBuilder().add("name", "Ada"))
                .build();
        McpReplayClientRequester requester = new McpReplayClientRequester(ALL_CAPS, Map.of("user_name", answer));
        McpInteractions interactions = new CdiInteractions(requester);

        assertThatThrownBy(() -> interactions
                        .batch()
                        .elicit("user_name", elicitationRequest(ALL_CAPS, null))
                        .roots("roots")
                        .awaitAll())
                .isInstanceOfSatisfying(McpInputRequiredBatchSignal.class, signal -> {
                    // only the still-missing "roots" is reported; the already-answered "user_name" is not re-requested
                    assertThat(signal.missing()).hasSize(1);
                    assertThat(signal.missing().get(0).key()).isEqualTo("roots");
                });
    }

    @Test
    void awaitAllReturnsEveryAnswerWhenAllAreKnown() {
        JsonObject elicitationAnswer = Json.createObjectBuilder()
                .add("action", "accept")
                .add("content", Json.createObjectBuilder().add("name", "Ada"))
                .build();
        JsonObject rootsAnswer = Json.createObjectBuilder()
                .add("roots", Json.createArrayBuilder())
                .build();
        McpReplayClientRequester requester =
                new McpReplayClientRequester(ALL_CAPS, Map.of("user_name", elicitationAnswer, "roots", rootsAnswer));
        McpInteractions interactions = new CdiInteractions(requester);

        McpInteractionResults results = interactions
                .batch()
                .elicit("user_name", elicitationRequest(ALL_CAPS, null))
                .roots("roots")
                .awaitAll();

        assertThat(results.elicitation("user_name").content().getString("name")).isEqualTo("Ada");
        assertThat(results.roots("roots")).isEmpty();
    }

    @Test
    void unknownKeyInResultsThrows() {
        JsonObject rootsAnswer = Json.createObjectBuilder()
                .add("roots", Json.createArrayBuilder())
                .build();
        McpReplayClientRequester requester = new McpReplayClientRequester(ALL_CAPS, Map.of("roots", rootsAnswer));
        McpInteractions interactions = new CdiInteractions(requester);

        McpInteractionResults results = interactions.batch().roots("roots").awaitAll();

        assertThatThrownBy(() -> results.roots("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown key");
    }

    @Test
    void wrongTypeKeyInResultsThrows() {
        JsonObject rootsAnswer = Json.createObjectBuilder()
                .add("roots", Json.createArrayBuilder())
                .build();
        McpReplayClientRequester requester = new McpReplayClientRequester(ALL_CAPS, Map.of("roots", rootsAnswer));
        McpInteractions interactions = new CdiInteractions(requester);

        McpInteractionResults results = interactions.batch().roots("roots").awaitAll();

        assertThatThrownBy(() -> results.elicitation("roots")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> results.sampling("roots")).isInstanceOf(IllegalArgumentException.class);
    }
}
