package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpReplayClientRequesterTest {

    static final McpProtocolContext PROTOCOL = new McpProtocolContext(
            McpEra.MODERN,
            "2026-07-28",
            Json.createObjectBuilder()
                    .add("elicitation", JsonValue.EMPTY_JSON_OBJECT)
                    .build(),
            null,
            null);

    @Test
    void knownResponsesAreReturnedInCallOrder() {
        JsonObject first = Json.createObjectBuilder().add("action", "accept").build();
        McpReplayClientRequester requester = new McpReplayClientRequester(PROTOCOL, Map.of("input-0", first));

        assertThat(requester.request("elicitation/create", Map.of(), Duration.ZERO))
                .isSameAs(first);
        assertThatThrownBy(() -> requester.request("sampling/createMessage", Map.of("maxTokens", 10), Duration.ZERO))
                .isInstanceOfSatisfying(McpInputRequiredSignal.class, signal -> {
                    assertThat(signal.key()).isEqualTo("input-1");
                    assertThat(signal.method()).isEqualTo("sampling/createMessage");
                    assertThat(signal.params()).containsEntry("maxTokens", 10);
                });
    }

    @Test
    void explicitKeyIsUsedVerbatimAndDoesNotConsumeTheAutoCounter() {
        JsonObject answer = Json.createObjectBuilder().add("action", "accept").build();
        McpReplayClientRequester requester = new McpReplayClientRequester(PROTOCOL, Map.of("user_name", answer));

        assertThat(requester.request("elicitation/create", Map.of(), Duration.ZERO, "user_name"))
                .isSameAs(answer);
        // the keyed call above must not have consumed the auto counter used for unkeyed calls
        assertThatThrownBy(() -> requester.request("sampling/createMessage", Map.of(), Duration.ZERO))
                .isInstanceOfSatisfying(
                        McpInputRequiredSignal.class,
                        signal -> assertThat(signal.key()).isEqualTo("input-0"));
    }

    @Test
    void missingExplicitKeyThrowsSignalUnderThatKey() {
        McpReplayClientRequester requester = new McpReplayClientRequester(PROTOCOL, Map.of());

        assertThatThrownBy(() ->
                        requester.request("elicitation/create", Map.of("message", "hi"), Duration.ZERO, "user_name"))
                .isInstanceOfSatisfying(McpInputRequiredSignal.class, signal -> {
                    assertThat(signal.key()).isEqualTo("user_name");
                    assertThat(signal.method()).isEqualTo("elicitation/create");
                });
    }

    @Test
    void nullKeyFallsBackToUnchangedAutoNumberedBehaviour() {
        JsonObject first = Json.createObjectBuilder().add("action", "accept").build();
        McpReplayClientRequester requester = new McpReplayClientRequester(PROTOCOL, Map.of("input-0", first));

        assertThat(requester.request("elicitation/create", Map.of(), Duration.ZERO, null))
                .isSameAs(first);
    }

    @Test
    void capabilitiesComeFromRequestMetadata() {
        McpReplayClientRequester requester = new McpReplayClientRequester(PROTOCOL, Map.of());

        assertThat(requester.isModern()).isTrue();
        assertThat(requester.supports("elicitation")).isTrue();
        requester.requireCapability("elicitation");
        assertThatThrownBy(() -> requester.requireCapability("roots"))
                .isInstanceOfSatisfying(
                        McpException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(-32021));
    }
}
