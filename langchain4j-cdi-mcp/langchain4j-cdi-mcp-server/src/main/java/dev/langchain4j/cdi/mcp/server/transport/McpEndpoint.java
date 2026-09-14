package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpProtocolErrors;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import dev.langchain4j.cdi.mcp.server.protocol.McpHttpHeaders;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * JAX-RS resource that implements the MCP Streamable HTTP transport at the {@code /mcp} endpoint. Validates the
 * {@code Origin} header, detects the protocol era of each request, and routes legacy (2025-03-26) requests to
 * {@link McpLegacyProtocolHandler}. Modern (2026-07-28) requests are rejected with {@code 404}/{@code -32601} until the
 * modern handler is introduced.
 */
@Path("/mcp")
@ApplicationScoped
public class McpEndpoint {

    private static final String FORBIDDEN_BODY =
            "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32600,\"message\":\"Forbidden: invalid Origin header\"}}";

    @Inject
    McpLegacyProtocolHandler legacy;

    @Inject
    McpServerConfigResolver config;

    /** No-arg constructor required by CDI proxying and JAX-RS runtimes. */
    public McpEndpoint() {}

    /**
     * Handles incoming JSON-RPC requests and client responses over HTTP POST.
     *
     * @param body the JSON-RPC request or response body
     * @param headers the HTTP request headers
     * @return the JSON-RPC response, either as JSON or as an SSE event
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.SERVER_SENT_EVENTS})
    public Response handlePost(String body, @Context HttpHeaders headers) {
        Response forbidden = rejectInvalidOrigin(headers);
        if (forbidden != null) {
            return forbidden;
        }
        if (McpJsonRpcParser.isJsonRpcResponse(body)) {
            return legacy.handleClientResponse(body);
        }
        JsonRpcRequest request = McpJsonRpcParser.parseRequest(body);
        if (request.getMethod() == null) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_REQUEST, "Missing method");
        }
        String sessionId = headers.getHeaderString(McpHttpHeaders.SESSION_ID);
        String accept = headers.getHeaderString(HttpHeaders.ACCEPT);
        boolean wantsSse = accept != null && accept.contains(MediaType.SERVER_SENT_EVENTS);

        McpProtocolContext protocol = McpEraDetector.detect(request, headers::getHeaderString);
        if (protocol.isModern()) {
            throw McpProtocolErrors.methodNotFound(request.getId(), request.getMethod());
        }
        return legacy.handle(request, sessionId, wantsSse);
    }

    /**
     * Opens an SSE stream for server-initiated notifications on an existing session.
     *
     * @param headers the HTTP request headers
     * @return an SSE streaming response, or 400 if no session ID is provided
     */
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response handleGet(@Context HttpHeaders headers) {
        Response forbidden = rejectInvalidOrigin(headers);
        return forbidden != null ? forbidden : legacy.openStream(headers.getHeaderString(McpHttpHeaders.SESSION_ID));
    }

    /**
     * Terminates an MCP session.
     *
     * @param headers the HTTP request headers
     * @return an OK response after the session is terminated
     */
    @DELETE
    public Response handleDelete(@Context HttpHeaders headers) {
        Response forbidden = rejectInvalidOrigin(headers);
        return forbidden != null ? forbidden : legacy.terminate(headers.getHeaderString(McpHttpHeaders.SESSION_ID));
    }

    private Response rejectInvalidOrigin(HttpHeaders headers) {
        boolean allowed = McpOriginValidator.isAllowed(
                headers.getHeaderString(McpHttpHeaders.ORIGIN),
                headers.getHeaderString(McpHttpHeaders.HOST),
                config.get().getAllowedOrigins());
        return allowed
                ? null
                : Response.status(Response.Status.FORBIDDEN)
                        .entity(FORBIDDEN_BODY)
                        .type(MediaType.APPLICATION_JSON)
                        .build();
    }
}
