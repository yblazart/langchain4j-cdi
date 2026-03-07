package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpToolNotFoundException;
import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import dev.langchain4j.cdi.mcp.server.logging.McpLogger;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcResponse;
import dev.langchain4j.cdi.mcp.server.protocol.McpImplementation;
import dev.langchain4j.cdi.mcp.server.protocol.McpInitializeResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptGetResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptMessage;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptWireFormat;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptsListResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpResourceReadResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpResourceWireFormat;
import dev.langchain4j.cdi.mcp.server.protocol.McpResourcesListResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpServerCapabilities;
import dev.langchain4j.cdi.mcp.server.protocol.McpToolCallResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpToolsListResult;
import dev.langchain4j.cdi.mcp.server.registry.McpBeanInvoker;
import dev.langchain4j.cdi.mcp.server.registry.McpPromptDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpPromptRegistry;
import dev.langchain4j.cdi.mcp.server.registry.McpResourceDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpResourceRegistry;
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
import jakarta.json.bind.JsonbConfig;
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
import java.util.List;
import java.util.Map;

@Path("/mcp")
@ApplicationScoped
public class McpEndpoint {

    @Inject
    McpToolRegistry toolRegistry;

    @Inject
    McpResourceRegistry resourceRegistry;

    @Inject
    McpPromptRegistry promptRegistry;

    @Inject
    McpSessionManager sessionManager;

    @Inject
    McpToolInvoker toolInvoker;

    @Inject
    McpBeanInvoker beanInvoker;

    @Inject
    McpNotificationBroadcaster broadcaster;

    @Inject
    McpLogger mcpLogger;

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
            case "resources/list" -> handleResourcesList(request, sessionId, wantsSse);
            case "resources/read" -> handleResourcesRead(request, sessionId, wantsSse);
            case "prompts/list" -> handlePromptsList(request, sessionId, wantsSse);
            case "prompts/get" -> handlePromptsGet(request, sessionId, wantsSse);
            case "logging/setLevel" -> handleLoggingSetLevel(request, sessionId);
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
        StreamingOutput stream = out -> {
            broadcaster.registerStream(sessionId, out);
            try {
                out.write(": stream opened\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                broadcaster.unregisterStream(sessionId);
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
                McpServerCapabilities.full(),
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

        return Response.ok(serializeToJson(JsonRpcResponse.success(request.getId(), result)))
                .type(MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", newSessionId)
                .build();
    }

    private Response handleInitialized(JsonRpcRequest request, String sessionId) {
        McpSession session = sessionManager.requireSession(request.getId(), sessionId);
        session.markInitialized();
        return Response.ok().build();
    }

    // --- Tools ---

    private Response handleToolsList(JsonRpcRequest request, String sessionId, boolean sse) {
        sessionManager.requireSession(request.getId(), sessionId);

        McpToolsListResult result = new McpToolsListResult(toolRegistry.listTools().stream()
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

        McpToolDescriptor tool = toolRegistry
                .findTool(toolName)
                .orElseThrow(() -> new McpToolNotFoundException(request.getId(), toolName));

        try {
            Object callResult = toolInvoker.invoke(tool, arguments);
            McpToolCallResult result;
            if (callResult == null) {
                result = McpToolCallResult.empty();
            } else {
                result = McpToolCallResult.text(callResult.toString());
            }
            return respond(request.getId(), result, sse);
        } catch (McpException e) {
            throw new McpException(request.getId(), e.getErrorCode(), e.getMessage());
        }
    }

    // --- Resources ---

    private Response handleResourcesList(JsonRpcRequest request, String sessionId, boolean sse) {
        sessionManager.requireSession(request.getId(), sessionId);

        McpResourcesListResult result = new McpResourcesListResult(resourceRegistry.listResources().stream()
                .map(r -> new McpResourceWireFormat(r.getUri(), r.getName(), r.getDescription(), r.getMimeType()))
                .toList());

        return respond(request.getId(), result, sse);
    }

    private Response handleResourcesRead(JsonRpcRequest request, String sessionId, boolean sse) {
        sessionManager.requireSession(request.getId(), sessionId);

        JsonObject params = request.getParams();
        String uri = params != null && params.containsKey("uri") ? params.getString("uri") : null;

        if (uri == null) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_PARAMS, "Missing resource URI");
        }

        McpResourceDescriptor resource = resourceRegistry
                .findResource(uri)
                .orElseThrow(() ->
                        new McpException(request.getId(), McpErrorCode.INVALID_PARAMS, "Resource not found: " + uri));

        try {
            Object content = beanInvoker.invoke(resource.getBeanType(), resource.getMethod(), null);
            String text = content != null ? content.toString() : "";
            McpResourceReadResult result = McpResourceReadResult.text(uri, resource.getMimeType(), text);
            return respond(request.getId(), result, sse);
        } catch (McpException e) {
            throw new McpException(request.getId(), e.getErrorCode(), e.getMessage());
        }
    }

    // --- Prompts ---

    private Response handlePromptsList(JsonRpcRequest request, String sessionId, boolean sse) {
        sessionManager.requireSession(request.getId(), sessionId);

        McpPromptsListResult result = new McpPromptsListResult(promptRegistry.listPrompts().stream()
                .map(p -> new McpPromptWireFormat(
                        p.getName(),
                        p.getDescription(),
                        p.getArguments().stream()
                                .map(a -> new McpPromptWireFormat.McpPromptArgWireFormat(
                                        a.name(), a.description(), a.required()))
                                .toList()))
                .toList());

        return respond(request.getId(), result, sse);
    }

    private Response handlePromptsGet(JsonRpcRequest request, String sessionId, boolean sse) {
        sessionManager.requireSession(request.getId(), sessionId);

        JsonObject params = request.getParams();
        String promptName = params != null && params.containsKey("name") ? params.getString("name") : null;

        if (promptName == null) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_PARAMS, "Missing prompt name");
        }

        JsonObject arguments =
                params != null && params.containsKey("arguments") ? params.getJsonObject("arguments") : null;

        McpPromptDescriptor prompt = promptRegistry
                .findPrompt(promptName)
                .orElseThrow(() -> new McpException(
                        request.getId(), McpErrorCode.INVALID_PARAMS, "Prompt not found: " + promptName));

        try {
            Object callResult = beanInvoker.invoke(prompt.getBeanType(), prompt.getMethod(), arguments);
            McpPromptGetResult result;
            if (callResult instanceof List<?> messages) {
                @SuppressWarnings("unchecked")
                List<McpPromptMessage> typedMessages = (List<McpPromptMessage>) messages;
                result = new McpPromptGetResult(prompt.getDescription(), typedMessages);
            } else {
                String text = callResult != null ? callResult.toString() : "";
                result = new McpPromptGetResult(prompt.getDescription(), List.of(McpPromptMessage.user(text)));
            }
            return respond(request.getId(), result, sse);
        } catch (McpException e) {
            throw new McpException(request.getId(), e.getErrorCode(), e.getMessage());
        }
    }

    // --- Logging ---

    private Response handleLoggingSetLevel(JsonRpcRequest request, String sessionId) {
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

    // --- Ping ---

    private Response handlePing(JsonRpcRequest request) {
        return Response.ok(serializeToJson(JsonRpcResponse.success(request.getId(), Map.of())))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    // --- Shared ---

    private Response respond(Object id, Object result, boolean sse) {
        JsonRpcResponse rpcResponse = JsonRpcResponse.success(id, result);
        String json = serializeToJson(rpcResponse);
        if (!sse) {
            return Response.ok(json).type(MediaType.APPLICATION_JSON).build();
        }
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
        JsonbConfig config = new JsonbConfig().withNullValues(false);
        try (Jsonb jsonb = JsonbBuilder.create(config)) {
            return jsonb.toJson(obj);
        } catch (Exception e) {
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "JSON serialization failed");
        }
    }
}
