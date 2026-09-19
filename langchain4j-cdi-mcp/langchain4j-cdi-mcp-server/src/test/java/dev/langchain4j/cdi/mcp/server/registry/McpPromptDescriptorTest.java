package dev.langchain4j.cdi.mcp.server.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.langchain4j.cdi.mcp.server.api.Elicitation;
import dev.langchain4j.cdi.mcp.server.error.McpArgumentDefinitionException;
import dev.langchain4j.cdi.mcp.server.fixtures.ArgumentBindingTool;
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

        @Prompt(name = "bad_default", description = "A default that is not an integer")
        public String badDefault(@PromptArg(name = "hours", defaultValue = "eight") int hours) {
            return "hours=" + hours;
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

    @Test
    void anArgumentWithADefaultIsNotRequired() throws Exception {
        var method = ArgumentBindingTool.class.getMethod("planDay", int.class);

        McpPromptDescriptor descriptor = McpPromptDescriptor.fromMethod(ArgumentBindingTool.class, method);

        assertThat(descriptor.getArguments())
                .extracting(McpPromptDescriptor.PromptArgument::name, McpPromptDescriptor.PromptArgument::required)
                .containsExactly(tuple("hours", false));
    }

    @Test
    void aDefaultValueThatDoesNotConvertFailsRegistration() throws Exception {
        var method = TestBean.class.getMethod("badDefault", int.class);

        assertThatThrownBy(() -> McpPromptDescriptor.fromMethod(TestBean.class, method))
                .isInstanceOf(McpArgumentDefinitionException.class)
                .hasMessage(
                        "Prompt 'bad_default', parameter 'hours': defaultValue \"eight\" cannot be converted to int");
    }
}
