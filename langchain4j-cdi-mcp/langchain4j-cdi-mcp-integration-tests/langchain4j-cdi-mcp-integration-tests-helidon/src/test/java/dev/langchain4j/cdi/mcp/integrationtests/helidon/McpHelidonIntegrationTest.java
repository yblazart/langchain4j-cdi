package dev.langchain4j.cdi.mcp.integrationtests.helidon;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
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
            assertThat(tools).hasSize(1);
            assertThat(tools.get(0).name()).isEqualTo("getWeather");
            assertThat(tools.get(0).description()).isEqualTo("Get the current weather for a given city");
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
