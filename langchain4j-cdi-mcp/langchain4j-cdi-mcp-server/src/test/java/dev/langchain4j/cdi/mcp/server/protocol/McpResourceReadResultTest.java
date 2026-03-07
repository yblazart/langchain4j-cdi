package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpResourceReadResultTest {

    @Test
    void shouldCreateTextResult() {
        McpResourceReadResult result = McpResourceReadResult.text("file:///f.txt", "text/plain", "content");

        assertThat(result.getContents()).hasSize(1);
        assertThat(result.getContents().get(0).getUri()).isEqualTo("file:///f.txt");
        assertThat(result.getContents().get(0).getMimeType()).isEqualTo("text/plain");
        assertThat(result.getContents().get(0).getText()).isEqualTo("content");
        assertThat(result.getContents().get(0).getBlob()).isNull();
    }

    @Test
    void shouldCreateBlobResult() {
        McpResourceReadResult result = McpResourceReadResult.blob("file:///img.png", "image/png", "aGVsbG8=");

        assertThat(result.getContents()).hasSize(1);
        assertThat(result.getContents().get(0).getBlob()).isEqualTo("aGVsbG8=");
        assertThat(result.getContents().get(0).getText()).isNull();
    }
}
