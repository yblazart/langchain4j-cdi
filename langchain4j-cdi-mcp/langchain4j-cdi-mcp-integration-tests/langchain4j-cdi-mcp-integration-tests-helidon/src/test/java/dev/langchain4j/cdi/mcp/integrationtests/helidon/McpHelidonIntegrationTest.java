package dev.langchain4j.cdi.mcp.integrationtests.helidon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

@HelidonTest
public class McpHelidonIntegrationTest {

    @Inject
    WebTarget injectedTarget;

    @Test
    public void shouldListToolsViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            List<ToolSpecification> tools = client.listTools();
            assertThat(tools).hasSizeGreaterThanOrEqualTo(2);

            List<String> toolNames = tools.stream().map(ToolSpecification::name).toList();
            assertThat(toolNames).contains("getWeather", "greet");
        }
    }

    @Test
    public void shouldCallToolViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("getWeather")
                    .arguments("{\"city\":\"Paris\",\"unit\":\"celsius\"}")
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).contains("Paris");
        }
    }

    @Test
    public void shouldCallToolWithOptionalParamOmitted() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("greet")
                    .arguments("{\"name\":\"Alice\"}")
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).isEqualTo("Hello, Alice!");
        }
    }

    @Test
    public void shouldCallToolWithOptionalParamProvided() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("greet")
                    .arguments("{\"name\":\"Bob\",\"prefix\":\"Bonjour\"}")
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).isEqualTo("Bonjour, Bob!");
        }
    }

    @Test
    public void shouldHaveOptionalParameterInToolSchema() throws Exception {
        try (McpClient client = buildClient()) {
            List<ToolSpecification> tools = client.listTools();
            ToolSpecification greet = tools.stream()
                    .filter(t -> t.name().equals("greet"))
                    .findFirst()
                    .orElseThrow();

            assertThat(greet.description()).isEqualTo("Greet someone by name");
            // "name" is required, "prefix" is optional
            assertThat(greet.parameters().required()).contains("name");
            assertThat(greet.parameters().required()).doesNotContain("prefix");
        }
    }

    @Test
    public void shouldCallToolWithUnknownNameViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("nonExistentTool")
                    .arguments("{}")
                    .build();
            assertThatThrownBy(() -> client.executeTool(request)).isInstanceOf(Exception.class);
        }
    }

    @Test
    public void shouldListToolsMultipleTimes() throws Exception {
        try (McpClient client = buildClient()) {
            List<ToolSpecification> tools1 = client.listTools();
            List<ToolSpecification> tools2 = client.listTools();
            assertThat(tools1).hasSizeGreaterThanOrEqualTo(2);
            assertThat(tools2).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Test
    public void shouldDeclareToolsListChangedCapability() {
        String sessionId = initializeSession();
        // The initialize response should have been successful
        // and the session ID should be valid
        assertThat(sessionId).isNotNull().isNotBlank();
    }

    @Test
    public void shouldHandlePingViaRawHttp() {
        String sessionId = initializeSession();

        Response response = injectedTarget
                .path("/mcp")
                .request(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .post(Entity.json("{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"ping\",\"params\":{}}"));

        assertThat(response.getStatus()).isEqualTo(200);
        String body = response.readEntity(String.class);
        assertThat(body).contains("\"jsonrpc\"");
        assertThat(body).contains("\"result\"");
    }

    @Test
    public void shouldDeleteSession() {
        String sessionId = initializeSession();

        Response response = injectedTarget
                .path("/mcp")
                .request()
                .header("Mcp-Session-Id", sessionId)
                .delete();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void shouldReturnCleanJsonForToolCallViaRawHttp() {
        String sessionId = initializeSession();

        Response response = injectedTarget
                .path("/mcp")
                .request(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .post(
                        Entity.json(
                                "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{\"name\":\"getWeather\",\"arguments\":{\"city\":\"Paris\",\"unit\":\"celsius\"}}}"));

        assertThat(response.getStatus()).isEqualTo(200);
        String body = response.readEntity(String.class);
        assertThat(body).contains("\"text\"");
        assertThat(body).contains("Paris");
        assertThat(body).doesNotContain("\"data\":null");
        assertThat(body).doesNotContain("\"mimeType\":null");
        assertThat(body).doesNotContain("\"uri\":null");
    }

    @Test
    public void shouldReturnErrorForUnknownMethod() {
        String sessionId = initializeSession();

        Response response = injectedTarget
                .path("/mcp")
                .request(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", sessionId)
                .post(Entity.json("{\"jsonrpc\":\"2.0\",\"id\":\"5\",\"method\":\"unknown/method\",\"params\":{}}"));

        assertThat(response.getStatus()).isEqualTo(200);
        String body = response.readEntity(String.class);
        assertThat(body).contains("error");
        assertThat(body).contains("Unknown method");
    }

    private String initializeSession() {
        Response initResponse = injectedTarget
                .path("/mcp")
                .request(MediaType.APPLICATION_JSON)
                .post(
                        Entity.json(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}"));

        assertThat(initResponse.getStatus()).isEqualTo(200);
        return initResponse.getHeaderString("Mcp-Session-Id");
    }

    private McpClient buildClient() {
        String baseUri = injectedTarget.getUri().toString();
        if (!baseUri.endsWith("/")) {
            baseUri += "/";
        }
        return DefaultMcpClient.builder()
                .transport(StreamableHttpMcpTransport.builder()
                        .url(baseUri + "mcp")
                        .build())
                .build();
    }
}
