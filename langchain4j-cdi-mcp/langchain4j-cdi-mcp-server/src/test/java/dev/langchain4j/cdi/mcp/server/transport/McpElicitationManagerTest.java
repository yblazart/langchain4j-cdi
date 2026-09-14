package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpElicitationManagerTest {

    static final class CapturingRequester implements McpClientRequester {
        final boolean modern;
        String method;
        Map<String, Object> params;

        CapturingRequester(boolean modern) {
            this.modern = modern;
        }

        @Override
        public boolean supports(String capability) {
            return true;
        }

        @Override
        public JsonObject request(String method, Map<String, Object> params, Duration timeout) {
            this.method = method;
            this.params = params;
            return JsonValue.EMPTY_JSON_OBJECT;
        }

        @Override
        public boolean isModern() {
            return modern;
        }
    }

    @Test
    void modernElicitationUsesFormModeAndObjectSchema() {
        CapturingRequester requester = new CapturingRequester(true);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Map.of("type", "string"));

        new McpElicitationManager().createElicitation(requester, "Your name?", properties, 30);

        assertThat(requester.method).isEqualTo("elicitation/create");
        assertThat(requester.params)
                .containsEntry("mode", "form")
                .containsEntry("message", "Your name?")
                .containsEntry("requestedSchema", Map.of("type", "object", "properties", properties));
    }

    @Test
    void legacyElicitationHasNoMode() {
        CapturingRequester requester = new CapturingRequester(false);

        new McpElicitationManager().createElicitation(requester, "m", Map.of(), 30);

        assertThat(requester.params).doesNotContainKey("mode").containsKey("requestedSchema");
    }
}
