package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.server.McpPrompt;
import dev.langchain4j.cdi.mcp.server.McpPromptArg;
import org.junit.jupiter.api.Test;

class McpPromptRegistryTest {

    @McpPrompt("Summarize text")
    public String summarize(@McpPromptArg("The text to summarize") String text) {
        return "Summary of: " + text;
    }

    @McpPrompt("Translate text")
    public String translate(
            @McpPromptArg("The text to translate") String text,
            @McpPromptArg(value = "Target language", required = false) String language) {
        return "Translated: " + text;
    }

    @Test
    void shouldRegisterAndFindPrompt() throws Exception {
        McpPromptRegistry registry = new McpPromptRegistry();
        McpPromptDescriptor descriptor =
                McpPromptDescriptor.fromMethod(getClass(), getClass().getMethod("summarize", String.class));

        registry.register(descriptor);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.findPrompt("summarize")).isPresent();
        assertThat(registry.findPrompt("summarize").get().getDescription()).isEqualTo("Summarize text");
        assertThat(registry.findPrompt("unknown")).isEmpty();
    }

    @Test
    void shouldParsePromptArguments() throws Exception {
        McpPromptDescriptor descriptor = McpPromptDescriptor.fromMethod(
                getClass(), getClass().getMethod("translate", String.class, String.class));

        assertThat(descriptor.getArguments()).hasSize(2);
        assertThat(descriptor.getArguments().get(0).name()).isEqualTo("text");
        assertThat(descriptor.getArguments().get(0).required()).isTrue();
        assertThat(descriptor.getArguments().get(1).name()).isEqualTo("language");
        assertThat(descriptor.getArguments().get(1).required()).isFalse();
    }

    @Test
    void shouldListPrompts() throws Exception {
        McpPromptRegistry registry = new McpPromptRegistry();
        registry.register(McpPromptDescriptor.fromMethod(getClass(), getClass().getMethod("summarize", String.class)));
        registry.register(McpPromptDescriptor.fromMethod(
                getClass(), getClass().getMethod("translate", String.class, String.class)));

        assertThat(registry.listPrompts()).hasSize(2);
    }
}
