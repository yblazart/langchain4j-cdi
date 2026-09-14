package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.api.McpRequestContext;
import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpProtocolErrors;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import dev.langchain4j.cdi.mcp.server.protocol.McpImplementation;
import dev.langchain4j.cdi.mcp.server.protocol.McpJsonSerializer;
import dev.langchain4j.cdi.mcp.server.protocol.McpMetaKeys;
import dev.langchain4j.cdi.mcp.server.protocol.McpProtocolVersions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Handles requests using MCP revision 2026-07-28 (stateless, per-request metadata). */
@ApplicationScoped
public class McpModernProtocolHandler {

    static final long DISCOVER_TTL_MS = 60_000L;

    protected McpFeatureService features;
    protected McpServerConfigResolver config;
    protected McpSubscriptionRegistry subscriptions;

    /** No-arg constructor required by CDI proxying. */
    public McpModernProtocolHandler() {}

    /**
     * Creates a handler backed by the given feature service, server configuration and subscription registry.
     *
     * @param features the shared business logic for tool/resource/prompt listing and invocation
     * @param config resolves the server configuration
     * @param subscriptions the registry of open {@code subscriptions/listen} streams
     */
    @Inject
    public McpModernProtocolHandler(
            McpFeatureService features, McpServerConfigResolver config, McpSubscriptionRegistry subscriptions) {
        this.features = features;
        this.config = config;
        this.subscriptions = subscriptions;
    }

    /**
     * Handles a single modern (2026-07-28) JSON-RPC request.
     *
     * @param request the parsed JSON-RPC request
     * @param protocol the protocol context detected for this request
     * @param acceptsSse whether the client's {@code Accept} header allows an SSE response
     * @return the reply, as JSON or as a request-scoped SSE stream
     */
    public McpReply handle(JsonRpcRequest request, McpProtocolContext protocol, boolean acceptsSse) {
        Object id = request.getId();
        JsonObject params = request.getParams() != null ? request.getParams() : JsonValue.EMPTY_JSON_OBJECT;
        try {
            return switch (request.getMethod()) {
                case "server/discover" -> ok(id, discover());
                case "tools/list" -> ok(id, complete(features.listTools(McpFeatureService.cursor(params))));
                case "resources/list" -> ok(id, complete(features.listResources(McpFeatureService.cursor(params))));
                case "resources/templates/list" ->
                    ok(id, complete(features.listResourceTemplates(McpFeatureService.cursor(params))));
                case "prompts/list" -> ok(id, complete(features.listPrompts(McpFeatureService.cursor(params))));
                case "completion/complete" -> ok(id, complete(features.complete(id, params)));
                case "tools/call", "prompts/get", "resources/read" -> invoke(request, protocol, acceptsSse);
                case "subscriptions/listen" -> listen(request, params);
                default -> throw McpProtocolErrors.methodNotFound(id, request.getMethod());
            };
        } catch (McpException e) {
            return McpReply.json(e.getHttpStatus(), rpcError(id, e));
        }
    }

    private JsonObject discover() {
        return complete(Json.createObjectBuilder()
                .add("supportedVersions", Json.createArrayBuilder(McpProtocolVersions.SUPPORTED))
                .add("capabilities", McpJsonSerializer.toJsonObject(features.capabilities()))
                .add("ttlMs", DISCOVER_TTL_MS)
                .add("cacheScope", "public")
                .build());
    }

    private McpReply invoke(JsonRpcRequest request, McpProtocolContext protocol, boolean acceptsSse) {
        AtomicBoolean cancelled = new AtomicBoolean();
        boolean stream = acceptsSse && (request.getProgressToken() != null || protocol.logLevel() != null);
        if (!stream) {
            return ok(request.getId(), execute(request, protocol, McpNoopResponseChannel.INSTANCE, cancelled));
        }
        return McpReply.sse(out -> {
            McpSseResponseChannel channel = new McpSseResponseChannel(out, cancelled);
            JsonObject message;
            try {
                message = rpcResult(request.getId(), execute(request, protocol, channel, cancelled));
            } catch (McpException e) {
                message = rpcError(request.getId(), e);
            }
            channel.send(message);
        });
    }

    /**
     * Opens a {@code subscriptions/listen} SSE stream: acknowledges the requested notification types, then delivers
     * matching change notifications until the server shuts down.
     *
     * @param request the JSON-RPC request
     * @param params the request parameters
     * @return the SSE reply
     */
    private McpReply listen(JsonRpcRequest request, JsonObject params) {
        JsonObject requested = params.get("notifications") instanceof JsonObject n ? n : JsonValue.EMPTY_JSON_OBJECT;
        McpNotificationFilter filter = McpNotificationFilter.from(requested);
        return McpReply.sse(out -> {
            McpSseResponseChannel channel = new McpSseResponseChannel(out, new AtomicBoolean());
            McpListenSubscription subscription = subscriptions.open(request.getId(), filter, channel);
            try {
                subscription.awaitClose();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                subscriptions.remove(subscription);
            }
        });
    }

    /**
     * Executes an invocation method and returns its decorated result. Overridden behaviour for MRTR is added in the
     * MRTR tasks.
     *
     * @param request the JSON-RPC request being invoked
     * @param protocol the protocol context for this request
     * @param channel the channel for request-scoped notifications (progress, logs)
     * @param cancelled flag set to {@code true} when the request is cancelled
     * @return the decorated invocation result
     */
    protected JsonObject execute(
            JsonRpcRequest request, McpProtocolContext protocol, McpResponseChannel channel, AtomicBoolean cancelled) {
        McpRequestContext ctx = new McpRequestContext(
                null,
                request.getId(),
                request.getProgressToken(),
                cancelled,
                protocol,
                unavailableRequester(protocol),
                channel);
        return complete(dispatchInvocation(request, ctx));
    }

    /**
     * Dispatches a {@code tools/call}, {@code prompts/get}, or {@code resources/read} request to the feature service,
     * using the stateless (session-less) overload.
     *
     * @param request the JSON-RPC request
     * @param ctx the request context
     * @return the raw (undecorated) invocation result
     */
    JsonObject dispatchInvocation(JsonRpcRequest request, McpRequestContext ctx) {
        Object id = request.getId();
        JsonObject params = request.getParams() != null ? request.getParams() : JsonValue.EMPTY_JSON_OBJECT;
        return switch (request.getMethod()) {
            case "tools/call" -> features.callTool(id, params, ctx, null);
            case "prompts/get" -> features.getPrompt(id, params, ctx, null);
            default -> features.readResource(id, params, ctx, null);
        };
    }

    static McpClientRequester unavailableRequester(McpProtocolContext protocol) {
        return new McpClientRequester() {
            @Override
            public boolean supports(String capability) {
                return protocol.hasClientCapability(capability);
            }

            @Override
            public JsonObject request(String method, Map<String, Object> params, Duration timeout) {
                throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "Client requests are not enabled");
            }

            @Override
            public boolean isModern() {
                return true;
            }

            @Override
            public void requireCapability(String capability) {
                if (!supports(capability)) {
                    throw McpProtocolErrors.missingClientCapability(null, capability);
                }
            }
        };
    }

    /**
     * Adds {@code resultType: "complete"} and server identity to a result.
     *
     * @param result the raw result
     * @return the decorated result
     */
    public JsonObject complete(JsonObject result) {
        return decorate(Json.createObjectBuilder(result).add("resultType", "complete"), result.getJsonObject("_meta"));
    }

    /**
     * Adds {@code _meta.io.modelcontextprotocol/serverInfo} to a result builder.
     *
     * @param result the result builder, already carrying its other fields
     * @param existingMeta the result's existing {@code _meta} object, or {@code null}
     * @return the built, decorated result
     */
    public JsonObject decorate(JsonObjectBuilder result, JsonObject existingMeta) {
        McpImplementation info = features.serverInfo();
        JsonObjectBuilder meta =
                existingMeta != null ? Json.createObjectBuilder(existingMeta) : Json.createObjectBuilder();
        meta.add(McpMetaKeys.SERVER_INFO, McpJsonSerializer.toJsonObject(info));
        return result.add("_meta", meta).build();
    }

    private static McpReply ok(Object id, JsonObject result) {
        return McpReply.json(200, rpcResult(id, result));
    }

    /**
     * Builds a JSON-RPC 2.0 success response envelope.
     *
     * @param id the request id
     * @param result the result payload
     * @return the JSON-RPC response
     */
    public static JsonObject rpcResult(Object id, JsonObject result) {
        return withId(Json.createObjectBuilder().add("jsonrpc", "2.0"), id)
                .add("result", result)
                .build();
    }

    /**
     * Builds a JSON-RPC 2.0 error response envelope.
     *
     * @param id the request id
     * @param e the exception describing the error
     * @return the JSON-RPC error response
     */
    public static JsonObject rpcError(Object id, McpException e) {
        JsonObjectBuilder error = Json.createObjectBuilder()
                .add("code", e.getErrorCode().getCode())
                .add("message", e.getMessage());
        if (e.getData() != null) {
            error.add("data", McpJsonSerializer.toJsonValue(e.getData()));
        }
        return withId(Json.createObjectBuilder().add("jsonrpc", "2.0"), id)
                .add("error", error)
                .build();
    }

    static JsonObjectBuilder withId(JsonObjectBuilder builder, Object id) {
        if (id instanceof String s) {
            builder.add("id", s);
        } else if (id instanceof Number n) {
            builder.add("id", n.longValue());
        }
        return builder;
    }
}
