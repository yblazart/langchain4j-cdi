package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpLegacyClientRequesterTest {

    @Test
    void delegatesToServerRequestManagerWithSessionId() {
        McpServerRequestManager manager = mock(McpServerRequestManager.class);
        JsonObject answer = Json.createObjectBuilder()
                .add("roots", Json.createArrayBuilder())
                .build();
        when(manager.sendRequest("s1", "roots/list", Map.of(), 7)).thenReturn(answer);
        JsonObject init = Json.createObjectBuilder()
                .add("capabilities", Json.createObjectBuilder().add("roots", JsonValue.EMPTY_JSON_OBJECT))
                .build();

        McpLegacyClientRequester requester = new McpLegacyClientRequester(new McpSession("s1", init), manager);

        assertThat(requester.request("roots/list", Map.of(), Duration.ofSeconds(7)))
                .isSameAs(answer);
        assertThat(requester.supports("roots")).isTrue();
        assertThat(requester.isModern()).isFalse();
        assertThat(requester.sessionId()).isEqualTo("s1");
    }
}
