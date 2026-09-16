package dev.langchain4j.cdi.mcp.server.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.protocol.McpSamplingMessage;
import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpSamplingManager;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CdiSamplingTest {

    static final class StubRequester implements McpClientRequester {

        private final JsonObject result;

        StubRequester(JsonObject result) {
            this.result = result;
        }

        @Override
        public boolean supports(String capability) {
            return true;
        }

        @Override
        public JsonObject request(String method, Map<String, Object> params, Duration timeout) {
            return result;
        }
    }

    static final class KeyCapturingRequester implements McpClientRequester {
        String key;
        private final JsonObject result;

        KeyCapturingRequester(JsonObject result) {
            this.result = result;
        }

        @Override
        public boolean supports(String capability) {
            return true;
        }

        @Override
        public JsonObject request(String method, Map<String, Object> params, Duration timeout) {
            return request(method, params, timeout, null);
        }

        @Override
        public JsonObject request(String method, Map<String, Object> params, Duration timeout, String key) {
            this.key = key;
            return result;
        }
    }

    private static SamplingResponse sample(JsonObject clientResult) {
        Sampling sampling = new CdiSampling(new StubRequester(clientResult), new McpSamplingManager());
        return sampling.requestBuilder()
                .addMessage(new McpSamplingMessage("user", Map.of("type", "text", "text", "hi")))
                .setMaxTokens(64)
                .build()
                .sendAndAwait();
    }

    @Test
    void samplingResponseCarriesTheModelsAnswer() {
        JsonObject clientResult = Json.createObjectBuilder()
                .add("role", "assistant")
                .add("content", Json.createObjectBuilder().add("type", "text").add("text", "Hello from the model"))
                .add("model", "test-model")
                .add("stopReason", "endTurn")
                .build();

        SamplingResponse response = sample(clientResult);

        assertThat(response.role()).isEqualTo("assistant");
        assertThat(response.content()).isInstanceOf(JsonObject.class);
        assertThat(((JsonObject) response.content()).getString("text")).isEqualTo("Hello from the model");
        assertThat(((JsonObject) response.content()).getString("type")).isEqualTo("text");
        assertThat(response.model()).isEqualTo("test-model");
        assertThat(response.stopReason()).isEqualTo("endTurn");
    }

    @Test
    void missingContentAndRoleStayNull() {
        SamplingResponse response =
                sample(Json.createObjectBuilder().add("model", "test-model").build());

        assertThat(response.content()).isNull();
        assertThat(response.role()).isNull();
        assertThat(response.model()).isEqualTo("test-model");
    }

    @Test
    void nonObjectContentIsStillReturned() {
        JsonObject clientResult = Json.createObjectBuilder()
                .add("role", "assistant")
                .add("content", "plain text answer")
                .build();

        SamplingResponse response = sample(clientResult);

        assertThat(response.content()).isEqualTo("plain text answer");
    }

    @Test
    void setKeyIsSentAsTheEffectiveInputRequestKey() {
        JsonObject clientResult = Json.createObjectBuilder()
                .add("role", "assistant")
                .add("content", "hi")
                .build();
        KeyCapturingRequester requester = new KeyCapturingRequester(clientResult);
        Sampling sampling = new CdiSampling(requester, new McpSamplingManager());

        sampling.requestBuilder()
                .addMessage(new McpSamplingMessage("user", Map.of("type", "text", "text", "hi")))
                .setMaxTokens(64)
                .setKey("summary")
                .build()
                .sendAndAwait();

        assertThat(requester.key).isEqualTo("summary");
    }

    @Test
    void blankKeyIsRejectedAtBuildTime() {
        Sampling sampling = new CdiSampling(new KeyCapturingRequester(null), new McpSamplingManager());

        assertThatThrownBy(() -> sampling.requestBuilder()
                        .addMessage(new McpSamplingMessage("user", Map.of("type", "text", "text", "hi")))
                        .setMaxTokens(64)
                        .setKey("  ")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
    }
}
