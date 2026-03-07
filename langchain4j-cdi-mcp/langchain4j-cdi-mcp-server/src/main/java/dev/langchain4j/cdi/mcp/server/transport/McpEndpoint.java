package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpToolNotFoundException;
import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import dev.langchain4j.cdi.mcp.server.logging.McpLogger;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcResponse;
import dev.langchain4j.cdi.mcp.server.protocol.McpCompletionResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpImplementation;
import dev.langchain4j.cdi.mcp.server.protocol.McpInitializeResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpPagination;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptGetResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptMessage;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptWireFormat;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptsListResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpResourceReadResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpResourceTemplateWireFormat;
import dev.langchain4j.cdi.mcp.server.protocol.McpResourceTemplatesListResult;
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
import dev.langchain4j.cdi.mcp.server.registry.McpResourceTemplateDescriptor;
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
    McpResourceSubscriptionManager subscriptionManager;

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
            case "resources/subscribe" -> handleResourcesSubscribe(request, sessionId);
            case "resources/unsubscribe" -> handleResourcesUnsubscribe(request, sessionId);
            case "resources/templates/list" -> handleResourcesTemplatesList(request, sessionId, wantsSse);
            case "prompts/list" -> handlePromptsList(request, sessionId, wantsSse);
            case "prompts/get" -> handlePromptsGet(request, sessionId, wantsSse);
            case "completion/complete" -> handleCompletionComplete(request, sessionId, wantsSse);
            case "logging/setLevel" -> handleLoggingSetLevel(request, sessionId);
            case "ping" -> handlePing(request);
            case "notifications/cancelled" -> handleNotificationsCancelled(request);
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

        String cursor = extractCursor(request.getParams());
        List<McpToolDescriptor> allTools = new java.util.ArrayList<>(toolRegistry.listTools());
        McpPagination.Page<McpToolDescriptor> page = McpPagination.paginate(allTools, cursor);

        McpToolsListResult result = new McpToolsListResult(
                page.items().stream().map(McpToolDescriptor::toWireFormat).toList(), page.nextCursor());

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

        String cursor = extractCursor(request.getParams());
        List<McpResourceDescriptor> allResources = new java.util.ArrayList<>(resourceRegistry.listResources());
        McpPagination.Page<McpResourceDescriptor> page = McpPagination.paginate(allResources, cursor);

        McpResourcesListResult result = new McpResourcesListResult(
                page.items().stream()
                        .map(r ->
                                new McpResourceWireFormat(r.getUri(), r.getName(), r.getDescription(), r.getMimeType()))
                        .toList(),
                page.nextCursor());

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

        String cursor = extractCursor(request.getParams());
        List<McpPromptDescriptor> allPrompts = new java.util.ArrayList<>(promptRegistry.listPrompts());
        McpPagination.Page<McpPromptDescriptor> page = McpPagination.paginate(allPrompts, cursor);

        McpPromptsListResult result = new McpPromptsListResult(
                page.items().stream()
                        .map(p -> new McpPromptWireFormat(
                                p.getName(),
                                p.getDescription(),
                                p.getArguments().stream()
                                        .map(a -> new McpPromptWireFormat.McpPromptArgWireFormat(
                                                a.name(), a.description(), a.required()))
                                        .toList()))
                        .toList(),
                page.nextCursor());

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

    // --- Resource Subscriptions ---

    private Response handleResourcesSubscribe(JsonRpcRequest request, String sessionId) {
        sessionManager.requireSession(request.getId(), sessionId);

        JsonObject params = request.getParams();
        String uri = params != null && params.containsKey("uri") ? params.getString("uri") : null;

        if (uri == null) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_PARAMS, "Missing resource URI");
        }

        subscriptionManager.subscribe(sessionId, uri);

        return Response.ok(serializeToJson(JsonRpcResponse.success(request.getId(), Map.of())))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private Response handleResourcesUnsubscribe(JsonRpcRequest request, String sessionId) {
        sessionManager.requireSession(request.getId(), sessionId);

        JsonObject params = request.getParams();
        String uri = params != null && params.containsKey("uri") ? params.getString("uri") : null;

        if (uri == null) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_PARAMS, "Missing resource URI");
        }

        subscriptionManager.unsubscribe(sessionId, uri);

        return Response.ok(serializeToJson(JsonRpcResponse.success(request.getId(), Map.of())))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    // --- Resource Templates ---

    private Response handleResourcesTemplatesList(JsonRpcRequest request, String sessionId, boolean sse) {
        sessionManager.requireSession(request.getId(), sessionId);

        String cursor = extractCursor(request.getParams());
        List<McpResourceTemplateDescriptor> allTemplates = new java.util.ArrayList<>(resourceRegistry.listTemplates());
        McpPagination.Page<McpResourceTemplateDescriptor> page = McpPagination.paginate(allTemplates, cursor);

        McpResourceTemplatesListResult result = new McpResourceTemplatesListResult(page.items().stream()
                .map(t -> new McpResourceTemplateWireFormat(
                        t.getUriTemplate(), t.getName(), t.getDescription(), t.getMimeType()))
                .toList());
        result.setNextCursor(page.nextCursor());

        return respond(request.getId(), result, sse);
    }

    // --- Completion ---

    private Response handleCompletionComplete(JsonRpcRequest request, String sessionId, boolean sse) {
        sessionManager.requireSession(request.getId(), sessionId);

        JsonObject params = request.getParams();
        JsonObject ref = params != null && params.containsKey("ref") ? params.getJsonObject("ref") : null;

        if (ref == null || !ref.containsKey("type")) {
            throw new McpException(request.getId(), McpErrorCode.INVALID_PARAMS, "Missing completion ref");
        }

        String refType = ref.getString("type");
        String refName = ref.containsKey("name") ? ref.getString("name") : null;
        JsonObject argument = params.containsKey("argument") ? params.getJsonObject("argument") : null;
        String argName = argument != null && argument.containsKey("name") ? argument.getString("name") : null;
        String argValue = argument != null && argument.containsKey("value") ? argument.getString("value") : "";

        McpCompletionResult result;
        if ("ref/prompt".equals(refType) && refName != null && argName != null) {
            result = completePromptArgument(refName, argName, argValue);
        } else if ("ref/resource".equals(refType) && refName != null) {
            result = completeResourceUri(refName, argValue);
        } else {
            result = McpCompletionResult.empty();
        }

        return respond(request.getId(), result, sse);
    }

    private McpCompletionResult completePromptArgument(String promptName, String argName, String prefix) {
        return promptRegistry
                .findPrompt(promptName)
                .map(prompt -> {
                    List<String> matchingArgs = prompt.getArguments().stream()
                            .map(McpPromptDescriptor.PromptArgument::name)
                            .filter(name -> name.startsWith(prefix))
                            .toList();
                    return McpCompletionResult.of(matchingArgs);
                })
                .orElse(McpCompletionResult.empty());
    }

    private McpCompletionResult completeResourceUri(String uriTemplatePrefix, String prefix) {
        List<String> matchingUris = resourceRegistry.listResources().stream()
                .map(McpResourceDescriptor::getUri)
                .filter(uri -> uri.startsWith(prefix))
                .toList();
        return McpCompletionResult.of(matchingUris);
    }

    // --- Notifications ---

    private Response handleNotificationsCancelled(JsonRpcRequest request) {
        return Response.ok().build();
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

    private String extractCursor(JsonObject params) {
        if (params != null && params.containsKey("cursor")) {
            return params.getString("cursor");
        }
        return null;
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
