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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Handles requests using MCP revision 2026-07-28 (stateless, per-request metadata). */
@ApplicationScoped
public class McpModernProtocolHandler {

    private static final Logger LOGGER = Logger.getLogger(McpModernProtocolHandler.class.getName());

    protected McpFeatureService features;
    protected McpServerConfigResolver config;
    protected McpSubscriptionRegistry subscriptions;
    protected McpMrtrSupport mrtr;
    protected McpContinuationStore continuations;

    /** No-arg constructor required by CDI proxying. */
    public McpModernProtocolHandler() {}

    /**
     * Creates a handler backed by the given feature service, server configuration, subscription registry, MRTR support
     * and continuation store.
     *
     * @param features the shared business logic for tool/resource/prompt listing and invocation
     * @param config resolves the server configuration
     * @param subscriptions the registry of open {@code subscriptions/listen} streams
     * @param mrtr the shared MRTR configuration and helpers
     * @param continuations the registry of suspended invocations (MRTR {@code CONTINUATION} mode)
     */
    @Inject
    public McpModernProtocolHandler(
            McpFeatureService features,
            McpServerConfigResolver config,
            McpSubscriptionRegistry subscriptions,
            McpMrtrSupport mrtr,
            McpContinuationStore continuations) {
        this.features = features;
        this.config = config;
        this.subscriptions = subscriptions;
        this.mrtr = mrtr;
        this.continuations = continuations;
    }

    /**
     * Returns the shared MRTR configuration and helpers.
     *
     * @return the MRTR support
     */
    McpMrtrSupport mrtrSupport() {
        return mrtr;
    }

    /**
     * Handles a single modern (2026-07-28) JSON-RPC request. Every failure is answered as a JSON-RPC error: malformed
     * parameters as {@code -32602 Invalid params} (HTTP 400), unexpected exceptions as {@code -32603 Internal error}
     * (HTTP 200). {@code subscriptions/listen} is not served here: it needs the {@code SseEventSink}-based route of
     * {@link McpEndpoint} selected by {@link McpListenRoutingFilter}, so a listen request reaching this method is
     * answered with {@code -32600 Invalid Request} (HTTP 400).
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
            validateParams(id, request.getMethod(), params);
            return switch (request.getMethod()) {
                case "server/discover" -> ok(id, discover());
                case "tools/list" ->
                    ok(id, complete(cacheable(features.listTools(McpFeatureService.cursor(params), true))));
                case "resources/list" ->
                    ok(id, complete(cacheable(features.listResources(McpFeatureService.cursor(params), true))));
                case "resources/templates/list" ->
                    ok(id, complete(cacheable(features.listResourceTemplates(McpFeatureService.cursor(params), true))));
                case "prompts/list" ->
                    ok(id, complete(cacheable(features.listPrompts(McpFeatureService.cursor(params), true))));
                case "completion/complete" -> ok(id, complete(features.complete(id, params)));
                case "tools/call", "prompts/get", "resources/read" -> invoke(request, protocol, acceptsSse);
                case McpListenRoutingFilter.LISTEN_METHOD -> throw listenNotRouted(id);
                default -> throw McpProtocolErrors.methodNotFound(id, request.getMethod());
            };
        } catch (McpException e) {
            return McpReply.json(e.getHttpStatus(), rpcError(id, e));
        } catch (McpInputRequiredSignal signal) {
            // handled inside execute(); never turned into an error response
            throw signal;
        } catch (RuntimeException e) {
            McpException error = internalError(id, request.getMethod(), e);
            return McpReply.json(error.getHttpStatus(), rpcError(id, error));
        }
    }

    private static McpException listenNotRouted(Object id) {
        return new McpException(
                id,
                McpErrorCode.INVALID_REQUEST,
                McpListenRoutingFilter.LISTEN_METHOD
                        + " must be sent as POST /mcp with matching Mcp-Method and MCP-Protocol-Version headers",
                400,
                null);
    }

    /**
     * Builds the {@code -32603 Internal error} answered for an unexpected exception, logging the exception.
     *
     * @param id the request id
     * @param method the JSON-RPC method being handled
     * @param e the unexpected exception
     * @return the JSON-RPC error to answer
     */
    static McpException internalError(Object id, String method, RuntimeException e) {
        LOGGER.log(Level.WARNING, "MCP: unexpected error handling " + method, e);
        return new McpException(id, McpErrorCode.INTERNAL_ERROR, "Internal error", 200, null);
    }

    /**
     * Rejects parameters whose JSON types do not match what the method expects, so that malformed requests are answered
     * with {@code -32602 Invalid params} instead of failing while the parameters are read.
     *
     * @param id the request id
     * @param method the JSON-RPC method
     * @param params the request parameters
     */
    static void validateParams(Object id, String method, JsonObject params) {
        switch (method) {
            case "tools/call", "prompts/get" -> {
                requireType(id, params, "name", JsonValue.ValueType.STRING);
                requireType(id, params, "arguments", JsonValue.ValueType.OBJECT);
                requireInvocationStateTypes(id, params);
            }
            case "resources/read" -> {
                requireType(id, params, "uri", JsonValue.ValueType.STRING);
                requireInvocationStateTypes(id, params);
            }
            case "tools/list", "resources/list", "resources/templates/list", "prompts/list" ->
                requireType(id, params, "cursor", JsonValue.ValueType.STRING);
            case "completion/complete" -> {
                requireType(id, params, "ref", JsonValue.ValueType.OBJECT);
                requireType(id, params, "argument", JsonValue.ValueType.OBJECT);
                if (params.get("ref") instanceof JsonObject ref) {
                    requireType(id, ref, "type", JsonValue.ValueType.STRING);
                    requireType(id, ref, "name", JsonValue.ValueType.STRING);
                }
                if (params.get("argument") instanceof JsonObject argument) {
                    requireType(id, argument, "name", JsonValue.ValueType.STRING);
                    requireType(id, argument, "value", JsonValue.ValueType.STRING);
                }
            }
            default -> {
                // no parameters read by this handler
            }
        }
    }

    private static void requireInvocationStateTypes(Object id, JsonObject params) {
        requireType(id, params, "requestState", JsonValue.ValueType.STRING);
        requireType(id, params, "inputResponses", JsonValue.ValueType.OBJECT);
    }

    private static void requireType(Object id, JsonObject object, String key, JsonValue.ValueType expected) {
        JsonValue value = object.get(key);
        if (value != null && value.getValueType() != expected) {
            throw McpProtocolErrors.invalidParams(
                    id,
                    "Invalid params: '" + key + "' must be a JSON "
                            + expected.name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Builds the {@code server/discover} result. Its {@code ttlMs} and {@code cacheScope} come from
     * {@link McpServerConfig} exactly like the five {@code CacheableResult} responses: an application that configures a
     * TTL means it for its whole surface, and {@code DiscoverResult} was the one place that ignored it.
     *
     * <p>The 2026-07-28 schema requires both fields on {@code DiscoverResult} but constrains neither beyond
     * {@code ttlMs >= 0}, and neither the conformance suite's {@code caching} scenario (which exercises
     * {@code tools/list}, {@code prompts/list}, {@code resources/list}, {@code resources/templates/list} and
     * {@code resources/read}, never {@code server/discover}) nor any {@code sep-2575-*} discover check asserts a
     * particular value — the suite's own mock servers answer discover with {@code ttlMs: 0}.
     *
     * @return the discover result, carrying the configured caching hints
     */
    private JsonObject discover() {
        return complete(cacheable(Json.createObjectBuilder()
                .add("supportedVersions", Json.createArrayBuilder(McpProtocolVersions.SUPPORTED))
                .add("capabilities", McpJsonSerializer.toJsonObject(features.capabilities()))
                .build()));
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
            } catch (McpInputRequiredSignal signal) {
                // handled inside execute(); never turned into an error response
                throw signal;
            } catch (RuntimeException e) {
                // the stream must always end with a final JSON-RPC response
                message = rpcError(request.getId(), internalError(request.getId(), request.getMethod(), e));
            }
            channel.send(message);
        });
    }

    /**
     * Opens a validated {@code subscriptions/listen} request on the given SSE channel and returns once the requested
     * notification types are acknowledged (the first message of the stream). The subscription then owns the channel: it
     * delivers matching change notifications until the server shuts down or the client is found gone, and closes the
     * channel when it ends. The calling thread is not held for the lifetime of the subscription.
     *
     * @param request the {@code subscriptions/listen} JSON-RPC request
     * @param channel the SSE channel of the response
     * @return the opened subscription, already closed if the acknowledgement could not be delivered
     */
    public McpListenSubscription listen(JsonRpcRequest request, McpSseChannel channel) {
        JsonObject params = request.getParams() != null ? request.getParams() : JsonValue.EMPTY_JSON_OBJECT;
        return subscriptions.open(request.getId(), listenFilter(params), channel);
    }

    private static McpNotificationFilter listenFilter(JsonObject params) {
        JsonObject requested = params.get("notifications") instanceof JsonObject n ? n : JsonValue.EMPTY_JSON_OBJECT;
        return McpNotificationFilter.from(requested);
    }

    /**
     * Executes an invocation method and returns its decorated result, replaying previously collected client-input
     * responses (MRTR {@code REPLAY} mode, the default).
     *
     * @param request the JSON-RPC request being invoked
     * @param protocol the protocol context for this request
     * @param channel the channel for request-scoped notifications (progress, logs)
     * @param cancelled flag set to {@code true} when the request is cancelled
     * @return the decorated invocation result
     */
    protected JsonObject execute(
            JsonRpcRequest request, McpProtocolContext protocol, McpResponseChannel channel, AtomicBoolean cancelled) {
        return mrtr.mode() == McpMrtrMode.CONTINUATION
                ? executeWithContinuation(request, protocol, channel, cancelled)
                : executeWithReplay(request, protocol, channel, cancelled);
    }

    /**
     * Executes an invocation, running it on a worker thread that blocks when it needs client input (MRTR
     * {@code CONTINUATION} mode). The first request (no {@code requestState}) starts the invocation; a retry with a
     * {@code requestState} supplies the pending answer and waits for the next event. Each round's channel and progress
     * token are attached to the continuation via {@link McpContinuation#useRound} before the worker starts or before
     * answers are supplied, so request-scoped notifications are always attributed to the current round, not to round 1;
     * the worker's own cancellation flag is the continuation-wide {@link McpContinuation#cancelledFlag()}, which is set
     * when any round's channel closes.
     *
     * @param request the JSON-RPC request being invoked
     * @param protocol the protocol context for this request
     * @param channel this round's channel for request-scoped notifications (progress, logs)
     * @param cancelled unused: this round's local flag would only reflect one HTTP round, not the whole invocation
     * @return the decorated invocation result, either {@code complete} or {@code input_required}
     */
    JsonObject executeWithContinuation(
            JsonRpcRequest request, McpProtocolContext protocol, McpResponseChannel channel, AtomicBoolean cancelled) {
        Object id = request.getId();
        String method = request.getMethod();
        JsonObject params = request.getParams() != null ? request.getParams() : JsonValue.EMPTY_JSON_OBJECT;
        String name = McpMrtrSupport.mrtrName(method, params);
        String digest = McpMrtrSupport.argumentsDigest(method, params);
        String token = params.getString("requestState", null);
        boolean logsRequested = protocol.logLevel() != null;

        McpContinuation continuation;
        if (token == null) {
            continuation = continuations.create();
            // set round 1 state before the worker starts, so early notifications are not attributed to no round
            continuation.useRound(channel, request.getProgressToken(), logsRequested);
            continuations.run(continuation, started -> {
                McpRequestContext ctx = new McpRequestContext(
                        null,
                        id,
                        request.getProgressToken(),
                        started.cancelledFlag(),
                        protocol,
                        new McpContinuationClientRequester(protocol, started),
                        started.channel());
                return dispatchInvocation(request, ctx);
            });
        } else {
            String continuationId =
                    mrtr.codec().decode(id, token, method, name, digest).continuationId();
            continuation = continuations
                    .find(continuationId)
                    .orElseThrow(() -> McpProtocolErrors.invalidParams(id, "Unknown or expired requestState"));
            // re-target this round's notifications before supplying answers, so they don't reach a stale round
            continuation.useRound(channel, request.getProgressToken(), logsRequested);
            McpInputRequiredSignal pendingInput = continuation.lastInput();
            JsonObject inputs = params.get("inputResponses") instanceof JsonObject o ? o : JsonValue.EMPTY_JSON_OBJECT;
            if (pendingInput != null
                    && continuation.isWaitingFor(pendingInput.key())
                    && continuation.supply(inputs) == 0) {
                return inputRequired(pendingInput, continuationState(method, name, digest, continuation));
            }
        }
        return awaitContinuation(id, method, name, digest, continuation);
    }

    private JsonObject awaitContinuation(
            Object id, String method, String name, String digest, McpContinuation continuation) {
        McpContinuation.Event event;
        try {
            event = continuation.nextEvent(mrtr.continuationTimeout());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            continuations.remove(continuation.id());
            continuation.cancel();
            throw new McpException(id, McpErrorCode.INTERNAL_ERROR, "Interrupted while waiting for the invocation");
        }
        if (event instanceof McpContinuation.Input input) {
            return inputRequired(input.request(), continuationState(method, name, digest, continuation));
        }
        continuations.remove(continuation.id());
        if (event instanceof McpContinuation.Done done) {
            return complete(done.result());
        }
        if (event instanceof McpContinuation.Failed failed) {
            throw failed.error();
        }
        continuation.cancel();
        throw new McpException(id, McpErrorCode.INTERNAL_ERROR, "Invocation timed out");
    }

    private String continuationState(String method, String name, String digest, McpContinuation continuation) {
        McpRequestStateCodec codec = mrtr.codec();
        return codec.encode(new McpRequestStateCodec.State(
                method, name, digest, codec.expiresAt(mrtr.stateTtl()), null, continuation.id()));
    }

    /**
     * Executes an invocation, re-running the method from the start and replaying any client-input responses collected
     * on earlier rounds. If the method raises {@link McpInputRequiredSignal}, a signed {@code requestState} carrying
     * the responses collected so far is returned to the client instead of propagating the exception.
     *
     * @param request the JSON-RPC request being invoked
     * @param protocol the protocol context for this request
     * @param channel the channel for request-scoped notifications (progress, logs)
     * @param cancelled flag set to {@code true} when the request is cancelled
     * @return the decorated invocation result, either {@code complete} or {@code input_required}
     */
    JsonObject executeWithReplay(
            JsonRpcRequest request, McpProtocolContext protocol, McpResponseChannel channel, AtomicBoolean cancelled) {
        Object id = request.getId();
        String method = request.getMethod();
        JsonObject params = request.getParams() != null ? request.getParams() : JsonValue.EMPTY_JSON_OBJECT;
        String name = McpMrtrSupport.mrtrName(method, params);
        String digest = McpMrtrSupport.argumentsDigest(method, params);

        JsonObject stateResponses = JsonValue.EMPTY_JSON_OBJECT;
        List<String> pendingKeys = List.of();
        String token = params.getString("requestState", null);
        if (token != null) {
            McpRequestStateCodec.State state = mrtr.codec().decode(id, token, method, name, digest);
            stateResponses = state.responses();
            pendingKeys = state.pendingKeys();
        }
        Map<String, JsonObject> collected = collectResponses(stateResponses, pendingKeys, params);

        McpRequestContext ctx = new McpRequestContext(
                null,
                id,
                request.getProgressToken(),
                cancelled,
                protocol,
                new McpReplayClientRequester(protocol, collected),
                channel);
        try {
            return complete(dispatchInvocation(request, ctx));
        } catch (McpInputRequiredSignal signal) {
            JsonObjectBuilder responses = Json.createObjectBuilder();
            collected.forEach(responses::add);
            McpRequestStateCodec codec = mrtr.codec();
            String state = codec.encode(new McpRequestStateCodec.State(
                    method,
                    name,
                    digest,
                    codec.expiresAt(mrtr.stateTtl()),
                    responses.build(),
                    null,
                    List.of(signal.key())));
            return inputRequired(signal, state);
        }
    }

    /**
     * Merges the input responses carried by a decoded {@code requestState} with those supplied on this round's
     * {@code inputResponses} parameter. Responses from the signed state are authoritative and never overridden; a
     * response supplied on this round is accepted only for a key the state issued as pending. Without a
     * {@code requestState} there are no pending keys, so {@code inputResponses} are ignored.
     *
     * @param stateResponses input responses previously collected, from the decoded {@code requestState}
     * @param pendingKeys input request keys the decoded {@code requestState} is waiting for
     * @param params the request parameters, which may carry an {@code inputResponses} object for this round
     * @return the merged responses, keyed by call order ({@code input-0}, {@code input-1}, …)
     */
    static Map<String, JsonObject> collectResponses(
            JsonObject stateResponses, List<String> pendingKeys, JsonObject params) {
        Map<String, JsonObject> collected = new LinkedHashMap<>();
        stateResponses.forEach((key, value) -> {
            if (value instanceof JsonObject o) {
                collected.put(key, o);
            }
        });
        if (params.get("inputResponses") instanceof JsonObject inputs) {
            inputs.forEach((key, value) -> {
                if (value instanceof JsonObject o && pendingKeys.contains(key) && !collected.containsKey(key)) {
                    collected.put(key, o);
                }
            });
        }
        return collected;
    }

    /**
     * Builds an {@code input_required} result for a single pending client request.
     *
     * @param signal the signal raised by the interrupted method, carrying the pending request's key/method/params
     * @param requestState the signed state to hand back to the client so it can retry with the missing answer
     * @return the decorated {@code input_required} result
     */
    public JsonObject inputRequired(McpInputRequiredSignal signal, String requestState) {
        JsonObject inputRequest = Json.createObjectBuilder()
                .add("method", signal.method())
                .add("params", McpJsonSerializer.toJsonObject(signal.params() != null ? signal.params() : Map.of()))
                .build();
        return decorate(
                Json.createObjectBuilder()
                        .add("resultType", "input_required")
                        .add("inputRequests", Json.createObjectBuilder().add(signal.key(), inputRequest))
                        .add("requestState", requestState),
                null);
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
            default -> cacheable(features.readResource(id, params, ctx, null));
        };
    }

    /**
     * Adds the SEP-2549 {@code ttlMs} and {@code cacheScope} caching hints to a result. The 2026-07-28 schema makes
     * both <em>required</em> on every {@code CacheableResult} - {@code ListToolsResult}, {@code ListPromptsResult},
     * {@code ListResourcesResult}, {@code ListResourceTemplatesResult} and {@code ReadResourceResult} - and on
     * {@code DiscoverResult}, so a result without them also fails generic wire-schema validation. The values come from
     * {@link McpServerConfig} and default to {@code ttlMs = 0} (immediately stale) and {@code cacheScope = "public"}.
     *
     * <p>Only called on the 2026-07-28 path: legacy-era results stay byte-identical.
     *
     * @param result the raw result
     * @return the result carrying its caching hints
     */
    JsonObject cacheable(JsonObject result) {
        McpServerConfig c = config.get();
        return Json.createObjectBuilder(result)
                .add("ttlMs", c.getCacheTtl().toMillis())
                .add("cacheScope", c.getCacheScope())
                .build();
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
