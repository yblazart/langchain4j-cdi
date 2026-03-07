package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpContentTest {

    @Test
    void shouldCreateTextContent() {
        McpContent content = McpContent.text("hello");

        assertThat(content.getType()).isEqualTo("text");
        assertThat(content.getText()).isEqualTo("hello");
        assertThat(content.isTextContent()).isTrue();
        assertThat(content.isImageContent()).isFalse();
        assertThat(content.isResourceContent()).isFalse();
        assertThat(content.getData()).isNull();
        assertThat(content.getMimeType()).isNull();
        assertThat(content.getUri()).isNull();
    }

    @Test
    void shouldCreateImageContent() {
        McpContent content = McpContent.image("aGVsbG8=", "image/png");

        assertThat(content.getType()).isEqualTo("image");
        assertThat(content.getData()).isEqualTo("aGVsbG8=");
        assertThat(content.getMimeType()).isEqualTo("image/png");
        assertThat(content.isImageContent()).isTrue();
        assertThat(content.isTextContent()).isFalse();
        assertThat(content.getText()).isNull();
    }

    @Test
    void shouldCreateResourceContent() {
        McpContent content = McpContent.resource("file:///data.json", "application/json", "{\"key\":\"value\"}");

        assertThat(content.getType()).isEqualTo("resource");
        assertThat(content.getUri()).isEqualTo("file:///data.json");
        assertThat(content.getMimeType()).isEqualTo("application/json");
        assertThat(content.getText()).isEqualTo("{\"key\":\"value\"}");
        assertThat(content.isResourceContent()).isTrue();
        assertThat(content.isTextContent()).isFalse();
        assertThat(content.isImageContent()).isFalse();
    }
}
