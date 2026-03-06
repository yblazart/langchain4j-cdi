package dev.langchain4j.cdi.mcp.integrationtests.helidon;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.integrationtests.McpTestRequests;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

@HelidonTest
public class McpHelidonIntegrationTest {

    @Inject
    WebTarget injectedTarget;

    @Test
    public void shouldCompleteFullMcpHandshake() {
        WebTarget target = injectedTarget.path("/mcp");

        // 1. initialize
        Response initResponse = target.request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(McpTestRequests.initializeRequest(), MediaType.APPLICATION_JSON));
        assertThat(initResponse.getStatus()).isEqualTo(200);
        String initResult = initResponse.readEntity(String.class);
        assertThat(initResult).contains("2025-03-26");

        String sessionId = initResponse.getHeaderString("Mcp-Session-Id");
        assertThat(sessionId).isNotNull().isNotBlank();

        // 2. tools/list
        Response listResponse = target.request(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .post(Entity.entity(McpTestRequests.toolsListRequest(), MediaType.APPLICATION_JSON));
        assertThat(listResponse.getStatus()).isEqualTo(200);
        String listResult = listResponse.readEntity(String.class);
        assertThat(listResult).contains("getWeather");

        // 3. tools/call
        Response callResponse = target.request(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .post(Entity.entity(
                        McpTestRequests.toolsCallRequest("getWeather", "{\"city\":\"Paris\",\"unit\":\"celsius\"}"),
                        MediaType.APPLICATION_JSON));
        assertThat(callResponse.getStatus()).isEqualTo(200);
        String callResult = callResponse.readEntity(String.class);
        assertThat(callResult).contains("Paris");
    }
}
