package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.resources.Resource;

class McpResourceRegistryTest {

    @Resource(uri = "test://data", name = "Test Data", description = "A test resource", mimeType = "text/plain")
    public String testResource() {
        return "test";
    }

    @Test
    void shouldRegisterAndFindResource() throws Exception {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceDescriptor descriptor =
                McpResourceDescriptor.fromMethod(getClass(), getClass().getMethod("testResource"));

        registry.register(descriptor);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.findResource("test://data")).isPresent();
        assertThat(registry.findResource("test://data").get().getName()).isEqualTo("Test Data");
        assertThat(registry.findResource("unknown://uri")).isEmpty();
    }

    @Test
    void shouldListResources() throws Exception {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceDescriptor descriptor =
                McpResourceDescriptor.fromMethod(getClass(), getClass().getMethod("testResource"));
        registry.register(descriptor);

        assertThat(registry.listResources()).hasSize(1);
    }

    private static McpResourceTemplateDescriptor template(String uriTemplate) {
        return new McpResourceTemplateDescriptor(
                uriTemplate, uriTemplate, "d", "text/plain", McpResourceRegistryTest.class, null);
    }

    @Test
    void shouldMatchARegisteredTemplateAgainstAnInstanceUri() {
        McpResourceRegistry registry = new McpResourceRegistry();
        registry.registerTemplate(template("test://template/{id}/data"));

        Optional<McpResourceRegistry.TemplateMatch> match = registry.matchTemplate("test://template/7/data");

        assertThat(match).isPresent();
        assertThat(match.get().template().getUriTemplate()).isEqualTo("test://template/{id}/data");
        assertThat(match.get().variables()).containsExactly(Map.entry("id", "7"));
    }

    @Test
    void shouldReturnNoMatchForAUriNoTemplateDescribes() {
        McpResourceRegistry registry = new McpResourceRegistry();
        registry.registerTemplate(template("test://template/{id}/data"));

        assertThat(registry.matchTemplate("other://thing")).isEmpty();
        assertThat(registry.matchTemplate(null)).isEmpty();
    }

    @Test
    void shouldPreferTheMoreSpecificTemplateWhenSeveralMatch() {
        McpResourceRegistry registry = new McpResourceRegistry();
        registry.registerTemplate(template("test://{a}/{b}"));
        registry.registerTemplate(template("test://fixed/{b}"));

        Optional<McpResourceRegistry.TemplateMatch> match = registry.matchTemplate("test://fixed/x");

        assertThat(match).isPresent();
        assertThat(match.get().template().getUriTemplate()).isEqualTo("test://fixed/{b}");
    }
}
