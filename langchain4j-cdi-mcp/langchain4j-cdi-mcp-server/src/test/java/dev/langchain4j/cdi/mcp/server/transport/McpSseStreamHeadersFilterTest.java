package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

class McpSseStreamHeadersFilterTest {

    final McpSseStreamHeadersFilter filter = new McpSseStreamHeadersFilter();
    final MultivaluedMap<String, Object> responseHeaders = new MultivaluedHashMap<>();

    void exchange(String method, String sessionId, int status) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getHeaderString("Mcp-Session-Id")).thenReturn(sessionId);
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getStatus()).thenReturn(status);
        when(response.getHeaders()).thenReturn(responseHeaders);
        filter.filter(request, response);
    }

    @Test
    void listenStreamsDisableCachingAndProxyBuffering() {
        exchange("POST", null, 200);

        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("no-cache");
        assertThat(responseHeaders.getFirst("X-Accel-Buffering")).isEqualTo("no");
        assertThat(responseHeaders).doesNotContainKey("Mcp-Session-Id");
    }

    @Test
    void legacyNotificationStreamsEchoTheSessionId() {
        exchange("GET", "session-1", 200);

        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("no-cache");
        assertThat(responseHeaders.getFirst("Mcp-Session-Id")).isEqualTo("session-1");
        assertThat(responseHeaders).doesNotContainKey("X-Accel-Buffering");
    }

    @Test
    void errorResponsesAreLeftUntouched() {
        exchange("POST", null, 400);
        exchange("GET", "session-1", 404);

        assertThat(responseHeaders).isEmpty();
    }
}
