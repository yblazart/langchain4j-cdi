package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.protocol.McpHttpHeaders;
import dev.langchain4j.cdi.mcp.server.protocol.McpProtocolVersions;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;

/**
 * Routes modern {@code subscriptions/listen} requests posted to {@code /mcp} to the {@code SseEventSink}-based resource
 * method of {@link McpEndpoint}. Jakarta REST selects resource methods from the path, HTTP method and media types only,
 * and an event sink can only be injected into a method dedicated to {@code text/event-stream}; the MCP headers
 * {@code Mcp-Method} and {@code MCP-Protocol-Version} identify listen requests without reading the request body. The
 * filter only acts on {@code POST /mcp} requests carrying both headers and leaves every other request untouched.
 */
@Provider
@PreMatching
public class McpListenRoutingFilter implements ContainerRequestFilter {

    static final String LISTEN_METHOD = "subscriptions/listen";
    private static final String MCP_PATH = "mcp";

    /** Creates a new filter. */
    public McpListenRoutingFilter() {}

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!HttpMethod.POST.equalsIgnoreCase(requestContext.getMethod())
                || !LISTEN_METHOD.equals(requestContext.getHeaderString(McpHttpHeaders.METHOD))
                || !McpProtocolVersions.isModernEra(requestContext.getHeaderString(McpHttpHeaders.PROTOCOL_VERSION))
                || !isMcpPath(requestContext.getUriInfo().getPath())) {
            return;
        }
        requestContext.setRequestUri(listenUri(requestContext.getUriInfo().getRequestUri()));
        requestContext.getHeaders().putSingle(HttpHeaders.ACCEPT, MediaType.SERVER_SENT_EVENTS);
    }

    private static boolean isMcpPath(String path) {
        if (path == null) {
            return false;
        }
        int start = path.startsWith("/") ? 1 : 0;
        int end = path.endsWith("/") && path.length() > start ? path.length() - 1 : path.length();
        return MCP_PATH.equals(path.substring(start, Math.max(start, end)));
    }

    private static URI listenUri(URI requestUri) {
        String raw = requestUri.toString();
        int queryStart = raw.indexOf('?');
        String path = queryStart < 0 ? raw : raw.substring(0, queryStart);
        String query = queryStart < 0 ? "" : raw.substring(queryStart);
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return URI.create(path + "/" + McpEndpoint.LISTEN_PATH + query);
    }
}
