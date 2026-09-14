package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.langchain4j.cdi.mcp.server.api.McpRequestContext;
import dev.langchain4j.cdi.mcp.server.error.McpToolNotFoundException;
import dev.langchain4j.cdi.mcp.server.registry.*;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpFeatureServiceTest {

    McpToolRegistry toolRegistry;
    McpToolInvoker toolInvoker;
    McpFeatureService service;

    @BeforeEach
    void setup() {
        toolRegistry = mock(McpToolRegistry.class);
        toolInvoker = mock(McpToolInvoker.class);
        service = new McpFeatureService(
                toolRegistry,
                mock(McpResourceRegistry.class),
                mock(McpPromptRegistry.class),
                toolInvoker,
                mock(McpBeanInvoker.class),
                new McpCancellationManager(),
                new McpServerConfigResolver(new McpServerConfig("srv", "1.2")));
    }

    @Test
    void listToolsReturnsJsonObjectWithToolsArray() {
        when(toolRegistry.listTools()).thenReturn(List.of());

        JsonObject result = service.listTools(null);

        assertThat(result.getJsonArray("tools")).isEmpty();
        assertThat(result.containsKey("nextCursor")).isFalse();
    }

    @Test
    void callUnknownToolFails() {
        when(toolRegistry.findTool("nope")).thenReturn(Optional.empty());
        JsonObject params = Json.createObjectBuilder().add("name", "nope").build();

        assertThatThrownBy(() -> service.callTool(1, params, ctx(), null)).isInstanceOf(McpToolNotFoundException.class);
    }

    @Test
    void callToolWrapsPlainResultAsTextContent() {
        McpToolDescriptor descriptor = mock(McpToolDescriptor.class);
        when(toolRegistry.findTool("greet")).thenReturn(Optional.of(descriptor));
        when(toolInvoker.invoke(eq(1), eq(descriptor), any(), any(), any())).thenReturn("Hello");
        JsonObject params = Json.createObjectBuilder().add("name", "greet").build();

        JsonObject result = service.callTool(1, params, ctx(), null);

        assertThat(result.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("Hello");
    }

    @Test
    void serverInfoComesFromConfig() {
        assertThat(service.serverInfo().name()).isEqualTo("srv");
        assertThat(service.serverInfo().version()).isEqualTo("1.2");
        assertThat(service.capabilities().completions()).isNotNull();
    }

    private static McpRequestContext ctx() {
        return new McpRequestContext(null, 1, null, new AtomicBoolean());
    }
}
