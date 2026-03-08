package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class McpToolCallResultEnhancedTest {

    @Test
    void shouldCreateImageResult() {
        McpToolCallResult result = McpToolCallResult.image("aGVsbG8=", "image/png");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getType()).isEqualTo("image");
        assertThat(result.getContent().get(0).getData()).isEqualTo("aGVsbG8=");
        assertThat(result.getContent().get(0).getMimeType()).isEqualTo("image/png");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldCreateResourceResult() {
        McpToolCallResult result = McpToolCallResult.resource("file:///f.txt", "text/plain", "content");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getType()).isEqualTo("resource");
        assertThat(result.getContent().get(0).getUri()).isEqualTo("file:///f.txt");
        assertThat(result.getContent().get(0).getText()).isEqualTo("content");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldCreateMixedContentResult() {
        List<McpContent> content =
                List.of(McpContent.text("Here is the image:"), McpContent.image("aGVsbG8=", "image/jpeg"));

        McpToolCallResult result = McpToolCallResult.of(content);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).isTextContent()).isTrue();
        assertThat(result.getContent().get(1).isImageContent()).isTrue();
        assertThat(result.isError()).isFalse();
    }
}
