package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.api.McpRequestContext;
import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import dev.langchain4j.cdi.mcp.server.logging.McpLogger;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcResponse;
import dev.langchain4j.cdi.mcp.server.protocol.McpHttpHeaders;
import dev.langchain4j.cdi.mcp.server.protocol.McpInitializeResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpProtocolVersions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Implements the MCP 2025-03-26 (legacy) protocol semantics: handshake-based session initialization, per-request
 * JSON-RPC dispatch, and broadcast-based server-initiated interactions over the session's SSE stream.
 */
@ApplicationScoped
public class McpLegacyProtocolHandler {

    private static final String HEADER_CACHE_CONTROL = "Cache-Control";
    private static final String HEADER_NO_CACHE = "no-cache";

    @Inject
    McpFeatureService features;

    @Inject
    McpSessionManager sessionManager;

    @Inject
    McpNotificationBroadcaster broadcaster;

    @Inject
    McpLogger mcpLogger;

    @Inject
    McpResourceSubscriptionManager subscriptionManager;

    @Inject
    McpServerRequestManager serverRequestManager;

    @Inject
    McpRootsManager rootsManager;

    @Inject
    McpCancellationManager cancellationManager;

    /** CDI constructor. */
    public McpLegacyProtocolHandler() {}

    /**
     * Dispatches a legacy JSON-RPC request to the appropriate handler.
     *
     * @param request the parsed JSON-RPC request
     * @param sessionId the {@code Mcp-Session-Id} header value, or {@code null}
     * @param wantsSse whether the client accepts {@code text/event-stream} responses
     * @return the JAX-RS response
     */
    public Response handle(JsonRpcRequest request, String sessionId, boolean wantsSse) {
        Object id = request.getId();
        JsonObject params = request.getParams();
        return switch (request.getMethod()) {
            case "initialize" -> initialize(request, wantsSse);
            case "notifications/initialized" -> {
                sessionManager.requireSession(id, sessionId).markInitialized();
                yield Response.ok().build();
            }
            case "tools/list" ->
                listing(id, sessionId, wantsSse, () -> features.listTools(McpFeatureService.cursor(params)));
            case "resources/list" ->
                listing(id, sessionId, wantsSse, () -> features.listResources(McpFeatureService.cursor(params)));
            case "resources/templates/list" ->
                listing(
                        id,
                        sessionId,
                        wantsSse,
                        () -> features.listResourceTemplates(McpFeatureService.cursor(params)));
            case "prompts/list" ->
                listing(id, sessionId, wantsSse, () -> features.listPrompts(McpFeatureService.cursor(params)));
            case "completion/complete" -> listing(id, sessionId, wantsSse, () -> features.complete(id, params));
            case "tools/call" -> invoke(request, sessionId, wantsSse, features::callTool);
            case "resources/read" -> invoke(request, sessionId, wantsSse, features::readResource);
            case "prompts/get" -> invoke(request, sessionId, wantsSse, features::getPrompt);
            case "resources/subscribe" -> subscription(request, sessionId, true);
            case "resources/unsubscribe" -> subscription(request, sessionId, false);
            case "logging/setLevel" -> setLevel(request, sessionId);
            case "ping" -> json(id, JsonValue.EMPTY_JSON_OBJECT, false);
            case "notifications/cancelled" -> cancelled(request);
            case "notifications/roots/list_changed" -> {
                if (sessionId != null) {
                    rootsManager.onRootsChanged(sessionId);
                }
                yield Response.ok().build();
            }
            default ->
                throw new McpException(id, McpErrorCode.METHOD_NOT_FOUND, "Unknown method: " + request.getMethod());
        };
    }

    /**
     * Validates a request opening the notification stream of a session, before any byte of the stream is produced.
     *
     * @param sessionId the MCP session identifier from the request header
     * @throws WebApplicationException with status 400 if no session ID is provided
     * @throws McpException if the session does not exist
     */
    public void validateStream(String sessionId) {
        if (sessionId == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST).build());
        }
        sessionManager.requireSession(null, sessionId);
    }

    /**
     * Opens the notification stream of a validated session on the given SSE channel: registers it, sends a
     * {@code stream opened} comment and returns. The broadcaster then owns the channel: it delivers server-initiated
     * notifications and requests for the session, and closes the channel when the client is found gone on a delivery or
     * when the server shuts down. The calling thread is not held for the lifetime of the stream.
     *
     * @param sessionId the MCP session identifier
     * @param channel the SSE channel of the response
     */
    public void openStream(String sessionId, McpSseChannel channel) {
        broadcaster.registerStream(sessionId, channel);
        channel.sendComment("stream opened");
        if (!channel.isOpen()) {
            broadcaster.unregisterStream(sessionId, channel);
            channel.close();
        }
    }

    /**
     * Terminates an MCP session.
     *
     * @param sessionId the MCP session identifier from the request header
     * @return an OK response after the session is terminated
     */
    public Response terminate(String sessionId) {
        if (sessionId != null) {
            sessionManager.terminateSession(sessionId);
        }
        return Response.ok().build();
    }

    /**
     * Handles a JSON-RPC response sent by the client in reply to a server-initiated request.
     *
     * @param body the raw JSON-RPC response body
     * @return an OK response
     */
    public Response handleClientResponse(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            JsonObject json = reader.readObject();
            Object id = McpJsonRpcParser.extractId(json);
            if (json.containsKey("result")) {
                JsonObject result = json.getJsonObject("result");
                serverRequestManager.handleResponse(id, result);
            } else if (json.containsKey("error")) {
                JsonObject error = json.getJsonObject("error");
                String message = error.containsKey("message") ? error.getString("message") : "Unknown error";
                serverRequestManager.handleErrorResponse(id, message);
            }
        }
        return Response.ok().build();
    }

    private Response initialize(JsonRpcRequest request, boolean wantsSse) {
        String newSessionId = sessionManager.createSession(request.getParams());

        McpInitializeResult result = new McpInitializeResult(
                McpProtocolVersions.LEGACY_2025_03_26, features.capabilities(), features.serverInfo());

        if (wantsSse) {
            String json = serializeToJson(JsonRpcResponse.success(request.getId(), result));
            String payload = "event: message\ndata: " + json + "\n\n";
            StreamingOutput stream = out -> {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.flush();
            };
            return Response.ok(stream, MediaType.SERVER_SENT_EVENTS)
                    .header(HEADER_CACHE_CONTROL, HEADER_NO_CACHE)
                    .header(McpHttpHeaders.SESSION_ID, newSessionId)
                    .build();
        }

        return Response.ok(serializeToJson(JsonRpcResponse.success(request.getId(), result)))
                .type(MediaType.APPLICATION_JSON)
                .header(McpHttpHeaders.SESSION_ID, newSessionId)
                .build();
    }

    @FunctionalInterface
    interface Invocation {
        JsonObject call(Object requestId, JsonObject params, McpRequestContext ctx, McpSession session);
    }

    private Response invoke(JsonRpcRequest request, String sessionId, boolean sse, Invocation invocation) {
        McpSession session = sessionManager.requireSession(request.getId(), sessionId);
        McpRequestContext ctx = new McpRequestContext(
                sessionId,
                request.getId(),
                request.getProgressToken(),
                new AtomicBoolean(),
                McpProtocolContext.legacy(McpProtocolVersions.LEGACY_2025_03_26),
                new McpLegacyClientRequester(session, serverRequestManager),
                new McpBroadcastResponseChannel(broadcaster));
        return json(request.getId(), invocation.call(request.getId(), request.getParams(), ctx, session), sse);
    }

    private Response listing(Object id, String sessionId, boolean sse, Supplier<JsonObject> supplier) {
        sessionManager.requireSession(id, sessionId);
        return json(id, supplier.get(), sse);
    }

    private Response subscription(JsonRpcRequest request, String sessionId, boolean subscribe) {
        sessionManager.requireSession(request.getId(), sessionId);

        JsonObject params = request.getParams();
        String uri = params != null && params.containsKey("uri") ? params.getString("uri") : null;

        if (uri == null) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_PARAMS, "Missing resource URI");
        }

        if (subscribe) {
            subscriptionManager.subscribe(sessionId, uri);
        } else {
            subscriptionManager.unsubscribe(sessionId, uri);
        }

        return Response.ok(serializeToJson(JsonRpcResponse.success(request.getId(), Map.of())))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private Response setLevel(JsonRpcRequest request, String sessionId) {
        sessionManager.requireSession(request.getId(), sessionId);

        JsonObject params = request.getParams();
        String level = params != null && params.containsKey("level") ? params.getString("level") : null;

        if (level == null) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_PARAMS, "Missing log level");
        }

        try {
            mcpLogger.setMinimumLevel(McpLogLevel.valueOf(level));
        } catch (IllegalArgumentException e) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_PARAMS, "Invalid log level: " + level);
        }

        return Response.ok(serializeToJson(JsonRpcResponse.success(request.getId(), Map.of())))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private Response cancelled(JsonRpcRequest request) {
        JsonObject params = request.getParams();
        if (params != null && params.containsKey("requestId")) {
            Object cancelledRequestId = McpJsonRpcParser.jsonPrimitive(params.get("requestId"));
            if (cancelledRequestId != null) {
                cancellationManager.cancel(cancelledRequestId);
            }
        }
        return Response.ok().build();
    }

    private Response json(Object id, JsonObject result, boolean sse) {
        JsonObjectBuilder rpc = Json.createObjectBuilder().add("jsonrpc", "2.0");
        addJsonRpcId(rpc, id);
        rpc.add("result", result);
        return sendResponse(rpc.build().toString(), sse);
    }

    private static void addJsonRpcId(JsonObjectBuilder builder, Object id) {
        if (id instanceof String s) {
            builder.add("id", s);
        } else if (id instanceof Number n) {
            builder.add("id", n.longValue());
        }
    }

    private Response sendResponse(String json, boolean sse) {
        if (!sse) {
            return Response.ok(json).type(MediaType.APPLICATION_JSON).build();
        }
        String payload = "event: message\ndata: " + json + "\n\n";
        StreamingOutput stream = out -> {
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
        };
        return Response.ok(stream, MediaType.SERVER_SENT_EVENTS)
                .header(HEADER_CACHE_CONTROL, HEADER_NO_CACHE)
                .build();
    }

    private String serializeToJson(Object obj) {
        JsonbConfig config = new JsonbConfig().withNullValues(false);
        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            return jsonb.toJson(obj);
        } catch (Exception e) {
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "JSON serialization failed");
        }
    }
}
