package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import org.junit.jupiter.api.Test;

class McpListenRoutingFilterTest {

    static final String ACCEPT_BOTH = "application/json, text/event-stream";

    final ContainerRequestContext ctx = mock(ContainerRequestContext.class);
    final MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
    final McpListenRoutingFilter filter = new McpListenRoutingFilter();

    void request(String method, String path, String uri, String mcpMethod, String version) {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(uriInfo.getRequestUri()).thenReturn(URI.create(uri));
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(ctx.getMethod()).thenReturn(method);
        when(ctx.getHeaderString("Mcp-Method")).thenReturn(mcpMethod);
        when(ctx.getHeaderString("MCP-Protocol-Version")).thenReturn(version);
        headers.putSingle("Accept", ACCEPT_BOTH);
        when(ctx.getHeaders()).thenReturn(headers);
    }

    @Test
    void routesModernListenRequestsToTheSinkResource() {
        request("POST", "mcp", "http://localhost:8080/app/mcp?x=1", "subscriptions/listen", "2026-07-28");

        filter.filter(ctx);

        verify(ctx).setRequestUri(URI.create("http://localhost:8080/app/mcp/" + McpEndpoint.LISTEN_PATH + "?x=1"));
        assertThat(headers.getFirst("Accept")).isEqualTo("text/event-stream");
    }

    @Test
    void acceptsLeadingAndTrailingSlashes() {
        request("POST", "/mcp/", "http://localhost/mcp/", "subscriptions/listen", "2026-07-28");

        filter.filter(ctx);

        verify(ctx).setRequestUri(URI.create("http://localhost/mcp/" + McpEndpoint.LISTEN_PATH));
    }

    @Test
    void leavesOtherHttpMethodsUntouched() {
        request("GET", "mcp", "http://localhost/mcp", "subscriptions/listen", "2026-07-28");

        filter.filter(ctx);

        assertUntouched();
    }

    @Test
    void leavesOtherPathsUntouched() {
        request("POST", "other/mcp", "http://localhost/other/mcp", "subscriptions/listen", "2026-07-28");

        filter.filter(ctx);

        assertUntouched();
    }

    @Test
    void leavesOtherMcpMethodsUntouched() {
        request("POST", "mcp", "http://localhost/mcp", "tools/call", "2026-07-28");

        filter.filter(ctx);

        assertUntouched();
    }

    @Test
    void leavesLegacyOrUnversionedRequestsUntouched() {
        request("POST", "mcp", "http://localhost/mcp", "subscriptions/listen", "2025-03-26");
        filter.filter(ctx);
        request("POST", "mcp", "http://localhost/mcp", "subscriptions/listen", null);
        filter.filter(ctx);

        assertUntouched();
    }

    private void assertUntouched() {
        verify(ctx, never()).setRequestUri(any(URI.class));
        verify(ctx, never()).setRequestUri(any(URI.class), any(URI.class));
        assertThat(headers.getFirst("Accept")).isEqualTo(ACCEPT_BOTH);
    }
}
