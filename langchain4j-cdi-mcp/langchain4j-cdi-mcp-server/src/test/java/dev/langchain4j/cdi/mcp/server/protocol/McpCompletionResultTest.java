package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class McpCompletionResultTest {

    @Test
    void shouldCreateEmptyResult() {
        McpCompletionResult result = McpCompletionResult.empty();

        assertThat(result.getCompletion().getValues()).isEmpty();
        assertThat(result.getCompletion().isHasMore()).isFalse();
        assertThat(result.getCompletion().getTotal()).isZero();
    }

    @Test
    void shouldCreateResultWithValues() {
        McpCompletionResult result = McpCompletionResult.of(List.of("foo", "bar"));

        assertThat(result.getCompletion().getValues()).containsExactly("foo", "bar");
        assertThat(result.getCompletion().getTotal()).isEqualTo(2);
    }
}
