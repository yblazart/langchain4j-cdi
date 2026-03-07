package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpToolNotFoundException;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcResponse;
import dev.langchain4j.cdi.mcp.server.protocol.McpImplementation;
import dev.langchain4j.cdi.mcp.server.protocol.McpInitializeResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpServerCapabilities;
import dev.langchain4j.cdi.mcp.server.protocol.McpToolCallResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpToolsListResult;
import dev.langchain4j.cdi.mcp.server.registry.McpToolDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpToolInvoker;
import dev.langchain4j.cdi.mcp.server.registry.McpToolRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Path("/mcp")
@ApplicationScoped
public class McpEndpoint {

    @Inject
    McpToolRegistry registry;

    @Inject
    McpSessionManager sessionManager;

    @Inject
    McpToolInvoker invoker;

    @Inject
    @Named("mcp-server")
    Instance<McpServerConfig> configInstance;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.SERVER_SENT_EVENTS})
    public Response handlePost(
            String body, @HeaderParam("Mcp-Session-Id") String sessionId, @HeaderParam("Accept") String accept) {

        JsonRpcRequest request = parseRequest(body);

        if (request.getMethod() == null) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_REQUEST, "Missing method");
        }

        boolean wantsSse = accept != null && accept.contains("text/event-stream");

        return switch (request.getMethod()) {
            case "initialize" -> handleInitialize(request, wantsSse);
            case "notifications/initialized" -> handleInitialized(request, sessionId);
            case "tools/list" -> handleToolsList(request, sessionId, wantsSse);
            case "tools/call" -> handleToolsCall(request, sessionId, wantsSse);
            case "ping" -> handlePing(request);
            default ->
                throw new McpException(
                        request.getId(), McpErrorCode.METHOD_NOT_FOUND, "Unknown method: " + request.getMethod());
        };
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response handleGet(@HeaderParam("Mcp-Session-Id") String sessionId) {
        if (sessionId == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        sessionManager.requireSession(null, sessionId);
        // Keep-alive SSE stream for server-initiated notifications (not used yet)
        StreamingOutput stream = out -> {
            // Send an initial comment to keep the connection open
            out.write(": stream opened\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            // Block until the client disconnects
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        return Response.ok(stream, MediaType.SERVER_SENT_EVENTS)
                .header("Cache-Control", "no-cache")
                .header("Mcp-Session-Id", sessionId)
                .build();
    }

    @DELETE
    public Response handleDelete(@HeaderParam("Mcp-Session-Id") String sessionId) {
        if (sessionId != null) {
            sessionManager.terminateSession(sessionId);
        }
        return Response.ok().build();
    }

    private Response handleInitialize(JsonRpcRequest request, boolean wantsSse) {
        String newSessionId = sessionManager.createSession(request.getParams());
        McpServerConfig config = resolveConfig();

        McpInitializeResult result = new McpInitializeResult(
                "2025-03-26",
                McpServerCapabilities.withTools(),
                new McpImplementation(config.getServerName(), config.getServerVersion()));

        if (wantsSse) {
            String json = serializeToJson(JsonRpcResponse.success(request.getId(), result));
            String payload = "event: message\ndata: " + json + "\n\n";
            StreamingOutput stream = out -> {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
                out.flush();
            };
            return Response.ok(stream, MediaType.SERVER_SENT_EVENTS)
                    .header("Cache-Control", "no-cache")
                    .header("Mcp-Session-Id", newSessionId)
                    .build();
        }

        return Response.ok(JsonRpcResponse.success(request.getId(), result))
                .type(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", newSessionId)
                .build();
    }

    private Response handleInitialized(JsonRpcRequest request, String sessionId) {
        McpSession session = sessionManager.requireSession(request.getId(), sessionId);
        session.markInitialized();
        return Response.ok().build();
    }

    private Response handleToolsList(JsonRpcRequest request, String sessionId, boolean sse) {
        sessionManager.requireSession(request.getId(), sessionId);

        McpToolsListResult result = new McpToolsListResult(registry.listTools().stream()
                .map(McpToolDescriptor::toWireFormat)
                .toList());

        return respond(request.getId(), result, sse);
    }

    private Response handleToolsCall(JsonRpcRequest request, String sessionId, boolean sse) {
        sessionManager.requireSession(request.getId(), sessionId);

        JsonObject params = request.getParams();
        String toolName =
                params != null && params.containsKey("name") ? ((JsonString) params.get("name")).getString() : null;
        JsonObject arguments =
                params != null && params.containsKey("arguments") ? params.getJsonObject("arguments") : null;

        if (toolName == null) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_PARAMS, "Missing tool name");
        }

        McpToolDescriptor tool =
                registry.findTool(toolName).orElseThrow(() -> new McpToolNotFoundException(request.getId(), toolName));

        try {
            Object callResult = invoker.invoke(tool, arguments);
            McpToolCallResult result;
            if (callResult == null) {
                result = McpToolCallResult.empty();
            } else {
                result = McpToolCallResult.text(callResult.toString());
            }
            return respond(request.getId(), result, sse);
        } catch (McpException e) {
            // Re-throw with correct request id
            throw new McpException(request.getId(), e.getErrorCode(), e.getMessage());
        }
    }

    private Response handlePing(JsonRpcRequest request) {
        return Response.ok(JsonRpcResponse.success(request.getId(), Map.of()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private Response respond(Object id, Object result, boolean sse) {
        JsonRpcResponse rpcResponse = JsonRpcResponse.success(id, result);
        if (!sse) {
            return Response.ok(rpcResponse).type(MediaType.APPLICATION_JSON).build();
        }
        // SSE one-shot response
        String json = serializeToJson(rpcResponse);
        String payload = "event: message\ndata: " + json + "\n\n";
        StreamingOutput stream = out -> {
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
        };
        return Response.ok(stream, MediaType.SERVER_SENT_EVENTS)
                .header("Cache-Control", "no-cache")
                .build();
    }

    private McpServerConfig resolveConfig() {
        if (configInstance != null && configInstance.isResolvable()) {
            return configInstance.get();
        }
        return new McpServerConfig();
    }

    private JsonRpcRequest parseRequest(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            JsonObject json = reader.readObject();
            Object id = extractId(json);
            String method = json.containsKey("method") ? json.getString("method") : null;
            JsonObject params = json.containsKey("params") ? json.getJsonObject("params") : null;
            return new JsonRpcRequest(id, method, params);
        }
    }

    private Object extractId(JsonObject json) {
        if (!json.containsKey("id")) {
            return null;
        }
        JsonValue idValue = json.get("id");
        if (idValue instanceof JsonString value) {
            return value.getString();
        }
        if (idValue.getValueType() == JsonValue.ValueType.NUMBER) {
            return json.getJsonNumber("id").longValue();
        }
        return idValue.toString();
    }

    private String serializeToJson(Object obj) {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.toJson(obj);
        } catch (Exception e) {
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "JSON serialization failed");
        }
    }
}
