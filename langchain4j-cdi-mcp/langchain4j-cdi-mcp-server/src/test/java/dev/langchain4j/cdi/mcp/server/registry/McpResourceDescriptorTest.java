package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.server.McpResource;
import org.junit.jupiter.api.Test;

class McpResourceDescriptorTest {

    @McpResource(uri = "data://config", name = "Config", description = "App config", mimeType = "application/json")
    public String getConfig() {
        return "{}";
    }

    @McpResource(uri = "data://status")
    public String getStatus() {
        return "ok";
    }

    @Test
    void shouldCreateFromAnnotatedMethod() throws Exception {
        McpResourceDescriptor descriptor =
                McpResourceDescriptor.fromMethod(getClass(), getClass().getMethod("getConfig"));

        assertThat(descriptor.getUri()).isEqualTo("data://config");
        assertThat(descriptor.getName()).isEqualTo("Config");
        assertThat(descriptor.getDescription()).isEqualTo("App config");
        assertThat(descriptor.getMimeType()).isEqualTo("application/json");
    }

    @Test
    void shouldUseDefaults() throws Exception {
        McpResourceDescriptor descriptor =
                McpResourceDescriptor.fromMethod(getClass(), getClass().getMethod("getStatus"));

        assertThat(descriptor.getUri()).isEqualTo("data://status");
        assertThat(descriptor.getName()).isEqualTo("getStatus");
        assertThat(descriptor.getDescription()).isEmpty();
        assertThat(descriptor.getMimeType()).isEqualTo("text/plain");
    }
}
