package dev.langchain4j.cdi.mcp.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpElicitationManager;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CdiElicitationTest {

    static final class CapturingLegacyRequester implements McpClientRequester {
        String method;
        Map<String, Object> params;

        @Override
        public boolean supports(String capability) {
            return true;
        }

        @Override
        public JsonObject request(String method, Map<String, Object> params, Duration timeout) {
            this.method = method;
            this.params = params;
            return Json.createObjectBuilder()
                    .add("action", "accept")
                    .add("content", Json.createObjectBuilder().add("name", "Ada"))
                    .build();
        }
    }

    @Test
    void legacyElicitationSendsWrappedObjectSchema() {
        CapturingLegacyRequester requester = new CapturingLegacyRequester();
        Elicitation elicitation = new CdiElicitation(requester, new McpElicitationManager());

        ElicitationResponse response = elicitation
                .requestBuilder()
                .setMessage("m")
                .addSchemaProperty("name", () -> Map.of("type", "string"))
                .build()
                .sendAndAwait();

        assertThat(requester.method).isEqualTo("elicitation/create");
        assertThat(requester.params)
                .doesNotContainKey("mode")
                .containsEntry(
                        "requestedSchema",
                        Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))));
        assertThat(response.action()).isEqualTo(ElicitationResponse.Action.ACCEPT);
        assertThat(response.content().getString("name")).isEqualTo("Ada");
    }
}
