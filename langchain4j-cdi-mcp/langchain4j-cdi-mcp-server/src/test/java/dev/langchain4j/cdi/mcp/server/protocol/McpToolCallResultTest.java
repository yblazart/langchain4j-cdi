package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpToolCallResultTest {

    @Test
    void shouldCreateTextResult() {
        McpToolCallResult result = McpToolCallResult.text("Hello");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getType()).isEqualTo("text");
        assertThat(result.getContent().get(0).getText()).isEqualTo("Hello");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldCreateErrorResult() {
        McpToolCallResult result = McpToolCallResult.error("Something went wrong");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getText()).isEqualTo("Something went wrong");
        assertThat(result.isError()).isTrue();
    }

    @Test
    void shouldCreateEmptyResult() {
        McpToolCallResult result = McpToolCallResult.empty();

        assertThat(result.getContent()).isEmpty();
        assertThat(result.isError()).isFalse();
    }
}
