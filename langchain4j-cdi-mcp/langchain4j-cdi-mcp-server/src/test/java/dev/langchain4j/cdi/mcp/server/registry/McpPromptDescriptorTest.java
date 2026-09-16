package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.server.api.Elicitation;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;

class McpPromptDescriptorTest {

    @SuppressWarnings("unused")
    static class TestBean {

        @Prompt(name = "summarize", description = "Summarize a text")
        public String summarize(
                @PromptArg(name = "source_text", description = "The text to summarize") String text,
                @PromptArg(description = "How many words", required = false) String length) {
            return text;
        }

        @Prompt(description = "Ask the user")
        public String ask(Elicitation elicitation, String topic) {
            return topic;
        }
    }

    @Test
    void shouldPreferTheAnnotationNameOverTheParameterName() throws Exception {
        var method = TestBean.class.getMethod("summarize", String.class, String.class);

        McpPromptDescriptor descriptor = McpPromptDescriptor.fromMethod(TestBean.class, method);

        assertThat(descriptor.getName()).isEqualTo("summarize");
        assertThat(descriptor.getArguments())
                .extracting(McpPromptDescriptor.PromptArgument::name)
                .containsExactly("source_text", "length");
        assertThat(descriptor.getArguments())
                .extracting(McpPromptDescriptor.PromptArgument::required)
                .containsExactly(true, false);
        assertThat(descriptor.getArguments().get(0).description()).isEqualTo("The text to summarize");
    }

    @Test
    void shouldNotAdvertiseFrameworkParametersAsArguments() throws Exception {
        var method = TestBean.class.getMethod("ask", Elicitation.class, String.class);

        McpPromptDescriptor descriptor = McpPromptDescriptor.fromMethod(TestBean.class, method);

        assertThat(descriptor.getName()).isEqualTo("ask");
        assertThat(descriptor.getArguments())
                .extracting(McpPromptDescriptor.PromptArgument::name)
                .containsExactly("topic");
    }
}
