package dev.langchain4j.cdi.mcp.server.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McpSessionExceptionTest {

    @Test
    void shouldCreateWithCorrectErrorCode() {
        McpSessionException ex = new McpSessionException("req-1", "session not found");

        assertThat(ex.getErrorCode()).isEqualTo(McpErrorCode.SESSION_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("session not found");
    }

    @Test
    void shouldCreateWithNumericId() {
        McpSessionException ex = new McpSessionException(5L, "missing");

        assertThat(ex.getRequestId()).isEqualTo(5L);
    }

    @Test
    void shouldDefaultToHttp404() {
        // an unknown or terminated session id is a 404 for the Streamable HTTP transport, not the generic 200
        assertThat(new McpSessionException("req-1", "session not found").getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void shouldKeepAnExplicitHttpStatus() {
        assertThat(new McpSessionException("req-1", "no session id", McpSessionException.BAD_REQUEST).getHttpStatus())
                .isEqualTo(400);
    }
}
