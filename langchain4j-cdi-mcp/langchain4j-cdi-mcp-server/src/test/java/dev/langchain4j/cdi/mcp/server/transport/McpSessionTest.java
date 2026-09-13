package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

class McpSessionTest {

    @Test
    void hasCapabilityReadsNestedCapabilitiesOfInitializeParams() {
        JsonObject init = Json.createObjectBuilder()
                .add("protocolVersion", "2025-03-26")
                .add("capabilities", Json.createObjectBuilder().add("elicitation", JsonValue.EMPTY_JSON_OBJECT))
                .build();

        McpSession session = new McpSession("s1", init);

        assertThat(session.hasCapability("elicitation")).isTrue();
        assertThat(session.hasCapability("sampling")).isFalse();
    }

    @Test
    void hasCapabilityFallsBackToTopLevelKeys() {
        JsonObject caps = Json.createObjectBuilder()
                .add("roots", JsonValue.EMPTY_JSON_OBJECT)
                .build();

        assertThat(new McpSession("s1", caps).hasCapability("roots")).isTrue();
    }

    @Test
    void hasCapabilityIsFalseWithoutParams() {
        assertThat(new McpSession("s1", null).hasCapability("roots")).isFalse();
    }
}
