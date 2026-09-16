package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.cdi.mcp.server.fixtures.IconFixtures;
import dev.langchain4j.cdi.mcp.server.registry.McpPromptDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpPromptRegistry;
import dev.langchain4j.cdi.mcp.server.registry.McpResourceDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpResourceRegistry;
import dev.langchain4j.cdi.mcp.server.registry.McpResourceTemplateDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpToolDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpToolRegistry;
import jakarta.json.JsonObject;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Icons on the wire. The 2026-07-28 schema carries {@code icons} on {@code Tool}, {@code Prompt}, {@code Resource} and
 * {@code ResourceTemplate}; the 2025-03-26 schema this server serves as its legacy era carries none of them, so the
 * legacy listings must stay byte-identical.
 */
class McpIconsWireTest {

    private McpToolRegistry toolRegistry;
    private McpResourceRegistry resourceRegistry;
    private McpPromptRegistry promptRegistry;
    private McpFeatureService service;

    @BeforeEach
    void setup() {
        toolRegistry = mock(McpToolRegistry.class);
        resourceRegistry = mock(McpResourceRegistry.class);
        promptRegistry = mock(McpPromptRegistry.class);
        service = new McpFeatureService(
                toolRegistry,
                resourceRegistry,
                promptRegistry,
                mock(dev.langchain4j.cdi.mcp.server.registry.McpToolInvoker.class),
                mock(dev.langchain4j.cdi.mcp.server.registry.McpBeanInvoker.class),
                new McpCancellationManager(),
                new McpServerConfigResolver(new McpServerConfig("srv", "1.0")));
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        return IconFixtures.IconedFeatures.class.getMethod(name, parameterTypes);
    }

    private void registerAll() throws Exception {
        when(toolRegistry.listTools())
                .thenReturn(List.of(
                        McpToolDescriptor.fromMethod(IconFixtures.IconedFeatures.class, method("tool", String.class))));
        when(resourceRegistry.listResources())
                .thenReturn(List.of(
                        McpResourceDescriptor.fromMethod(IconFixtures.IconedFeatures.class, method("resource"))));
        when(resourceRegistry.listTemplates())
                .thenReturn(List.of(McpResourceTemplateDescriptor.fromMethod(
                        IconFixtures.IconedFeatures.class, method("template", String.class))));
        when(promptRegistry.listPrompts())
                .thenReturn(
                        List.of(McpPromptDescriptor.fromMethod(IconFixtures.IconedFeatures.class, method("prompt"))));
    }

    @Test
    void modernToolListingCarriesIcons() throws Exception {
        registerAll();

        JsonObject tool = service.listTools(null, true).getJsonArray("tools").getJsonObject(0);

        assertThat(tool.getJsonArray("icons").toString())
                .isEqualTo(
                        "[{\"mimeType\":\"image/png\",\"sizes\":[\"48x48\"],\"src\":\"https://icons.example/TOOL/iconed_tool.png\",\"theme\":\"dark\"}]");
    }

    @Test
    void modernPromptListingCarriesIcons() throws Exception {
        registerAll();

        JsonObject prompt =
                service.listPrompts(null, true).getJsonArray("prompts").getJsonObject(0);

        assertThat(prompt.getJsonArray("icons").getJsonObject(0).getString("src"))
                .isEqualTo("https://icons.example/PROMPT/iconed_prompt.png");
    }

    @Test
    void modernResourceListingCarriesIcons() throws Exception {
        registerAll();

        JsonObject resource =
                service.listResources(null, true).getJsonArray("resources").getJsonObject(0);

        assertThat(resource.getJsonArray("icons").getJsonObject(0).getString("src"))
                .isEqualTo("https://icons.example/RESOURCE/iconed_resource.png");
    }

    @Test
    void modernResourceTemplateListingCarriesIcons() throws Exception {
        registerAll();

        JsonObject template = service.listResourceTemplates(null, true)
                .getJsonArray("resourceTemplates")
                .getJsonObject(0);

        assertThat(template.getJsonArray("icons").getJsonObject(0).getString("src"))
                .isEqualTo("https://icons.example/RESOURCE_TEMPLATE/iconed_template.png");
    }

    @Test
    void legacyListingsAreByteIdenticalAndCarryNoIcons() throws Exception {
        registerAll();

        assertThat(service.listTools(null).toString())
                .isEqualTo(
                        "{\"tools\":[{\"description\":\"a tool with icons\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\"}},\"required\":[\"input\"]},\"name\":\"iconed_tool\"}]}");
        assertThat(service.listPrompts(null).toString())
                .isEqualTo(
                        "{\"prompts\":[{\"arguments\":[],\"description\":\"a prompt with icons\",\"name\":\"iconed_prompt\"}]}");
        assertThat(service.listResources(null).toString())
                .isEqualTo(
                        "{\"resources\":[{\"description\":\"a resource with icons\",\"mimeType\":\"text/plain\",\"name\":\"iconed_resource\",\"uri\":\"test://iconed\"}]}");
        assertThat(service.listResourceTemplates(null).toString())
                .isEqualTo(
                        "{\"resourceTemplates\":[{\"description\":\"a template with icons\",\"mimeType\":\"text/plain\",\"name\":\"iconed_template\",\"uriTemplate\":\"test://iconed/{id}\"}]}");
    }

    @Test
    void modernListingOmitsIconsKeyWhenProviderReturnsNothing() throws Exception {
        when(toolRegistry.listTools())
                .thenReturn(List.of(McpToolDescriptor.fromMethod(
                        IconFixtures.NoIconFeatures.class, IconFixtures.NoIconFeatures.class.getMethod("empty"))));

        JsonObject tool = service.listTools(null, true).getJsonArray("tools").getJsonObject(0);

        assertThat(tool).doesNotContainKey("icons");
    }
}
