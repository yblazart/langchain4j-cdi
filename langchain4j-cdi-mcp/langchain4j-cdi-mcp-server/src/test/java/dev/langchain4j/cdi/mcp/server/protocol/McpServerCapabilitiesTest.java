package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpServerCapabilitiesTest {

    @Test
    void fullShouldIncludeAllCapabilities() {
        McpServerCapabilities caps = McpServerCapabilities.full();

        assertThat(caps.getTools()).isNotNull();
        assertThat(caps.getTools()).containsEntry("listChanged", true);
        assertThat(caps.getResources()).isNotNull();
        assertThat(caps.getPrompts()).isNotNull();
        assertThat(caps.getPrompts()).containsEntry("listChanged", true);
        assertThat(caps.getLogging()).isNotNull();
    }

    @Test
    void withToolsShouldOnlyHaveTools() {
        McpServerCapabilities caps = McpServerCapabilities.withTools();

        assertThat(caps.getTools()).isNotNull();
        assertThat(caps.getResources()).isNull();
        assertThat(caps.getPrompts()).isNull();
        assertThat(caps.getLogging()).isNull();
    }
}
