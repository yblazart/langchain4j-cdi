package dev.langchain4j.cdi.mcp.integrationtests.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.integrationtests.McpTestRequests;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class McpQuarkusIntegrationTest {

    @ConfigProperty(name = "quarkus.http.test-port")
    int port;

    @Test
    public void shouldCompleteFullMcpHandshake() {
        String mcpEndpoint = "http://localhost:" + port + "/mcp";
        try (Client client = ClientBuilder.newClient()) {
            WebTarget target = client.target(mcpEndpoint);

            // 1. initialize
            Response initResponse = target.request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(McpTestRequests.initializeRequest(), MediaType.APPLICATION_JSON));
            assertThat(initResponse.getStatus()).isEqualTo(200);
            String initResult = initResponse.readEntity(String.class);
            assertThat(initResult).contains("2025-03-26");
            assertThat(initResult).contains("tools");

            String sessionId = initResponse.getHeaderString("Mcp-Session-Id");
            assertThat(sessionId).isNotNull().isNotBlank();

            // 2. notifications/initialized
            Response initializedResponse = target.request(MediaType.APPLICATION_JSON)
                    .header("Mcp-Session-Id", sessionId)
                    .post(Entity.entity(McpTestRequests.initializedNotification(), MediaType.APPLICATION_JSON));
            assertThat(initializedResponse.getStatus()).isEqualTo(200);

            // 3. tools/list
            Response listResponse = target.request(MediaType.APPLICATION_JSON)
                    .header("Mcp-Session-Id", sessionId)
                    .post(Entity.entity(McpTestRequests.toolsListRequest(), MediaType.APPLICATION_JSON));
            assertThat(listResponse.getStatus()).isEqualTo(200);
            String listResult = listResponse.readEntity(String.class);
            assertThat(listResult).contains("getWeather");

            // 4. tools/call
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

    @Test
    public void shouldReturnErrorForMissingSession() {
        String mcpEndpoint = "http://localhost:" + port + "/mcp";
        try (Client client = ClientBuilder.newClient()) {
            WebTarget target = client.target(mcpEndpoint);

            Response response = target.request(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(McpTestRequests.toolsListRequest(), MediaType.APPLICATION_JSON));
            assertThat(response.getStatus()).isEqualTo(200);
            String result = response.readEntity(String.class);
            assertThat(result).contains("-32001");
        }
    }
}
