package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.protocol.McpHttpHeaders;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

/**
 * Adds the stream response headers to the {@code SseEventSink}-based methods of {@link McpEndpoint} (marked with
 * {@link McpSseStream}), whose responses are built by the Jakarta REST runtime: {@code Cache-Control: no-cache} on
 * every stream, {@code X-Accel-Buffering: no} on {@code subscriptions/listen} streams and the {@code Mcp-Session-Id} of
 * the session on legacy {@code GET} notification streams. Error responses are left untouched. Runtimes that do not
 * apply response filters to SSE responses (Quarkus REST) send the streams without these headers.
 */
@Provider
@McpSseStream
public class McpSseStreamHeadersFilter implements ContainerResponseFilter {

    private static final int OK = 200;

    /** Creates a new filter. */
    public McpSseStreamHeadersFilter() {}

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (responseContext.getStatus() != OK) {
            return;
        }
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        headers.putSingle("Cache-Control", "no-cache");
        if (HttpMethod.GET.equalsIgnoreCase(requestContext.getMethod())) {
            String sessionId = requestContext.getHeaderString(McpHttpHeaders.SESSION_ID);
            if (sessionId != null) {
                headers.putSingle(McpHttpHeaders.SESSION_ID, sessionId);
            }
        } else {
            headers.putSingle("X-Accel-Buffering", "no");
        }
    }
}
