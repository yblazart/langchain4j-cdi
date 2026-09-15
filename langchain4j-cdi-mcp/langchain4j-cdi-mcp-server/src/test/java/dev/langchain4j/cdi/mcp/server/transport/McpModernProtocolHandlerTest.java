package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import dev.langchain4j.cdi.mcp.server.api.McpRequestContext;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpProtocolErrors;
import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import dev.langchain4j.cdi.mcp.server.protocol.McpImplementation;
import dev.langchain4j.cdi.mcp.server.protocol.McpServerCapabilities;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class McpModernProtocolHandlerTest {

    McpFeatureService features;
    McpModernProtocolHandler handler;
    McpSubscriptionRegistry registry;
    McpContinuationStore store;

    @BeforeEach
    void setup() {
        features = mock(McpFeatureService.class);
        when(features.serverInfo()).thenReturn(new McpImplementation("srv", "1.0"));
        when(features.capabilities())
                .thenReturn(new McpServerCapabilities(
                        new McpServerCapabilities.ToolsCapability(true),
                        new McpServerCapabilities.ResourcesCapability(true, true),
                        new McpServerCapabilities.PromptsCapability(true),
                        McpServerCapabilities.LoggingCapability.INSTANCE,
                        McpServerCapabilities.CompletionsCapability.INSTANCE));
        registry = new McpSubscriptionRegistry();
        McpServerConfigResolver resolver = new McpServerConfigResolver(new McpServerConfig());
        McpMrtrSupport support = new McpMrtrSupport(resolver);
        store = new McpContinuationStore(support);
        handler = new McpModernProtocolHandler(features, resolver, registry, support, store);
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
        store.shutdown();
    }

    static McpProtocolContext modern(McpLogLevel level, String... capabilities) {
        var caps = Json.createObjectBuilder();
        for (String c : capabilities) {
            caps.add(c, JsonValue.EMPTY_JSON_OBJECT);
        }
        return new McpProtocolContext(McpEra.MODERN, "2026-07-28", caps.build(), null, level);
    }

    static JsonObject parse(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    @Test
    void discoverAdvertisesVersionsCapabilitiesAndServerInfo() {
        McpReply reply = handler.handle(new JsonRpcRequest("d1", "server/discover", null), modern(null), false);

        assertThat(reply.status()).isEqualTo(200);
        JsonObject result = parse(reply.body()).getJsonObject("result");
        assertThat(result.getString("resultType")).isEqualTo("complete");
        assertThat(result.getJsonArray("supportedVersions").getString(0)).isEqualTo("2026-07-28");
        assertThat(result.getJsonArray("supportedVersions").getString(1)).isEqualTo("2025-03-26");
        assertThat(result.getJsonObject("capabilities").containsKey("tools")).isTrue();
        assertThat(result.getJsonNumber("ttlMs").longValue()).isPositive();
        assertThat(result.getString("cacheScope")).isEqualTo("public");
        assertThat(result.getJsonObject("_meta")
                        .getJsonObject("io.modelcontextprotocol/serverInfo")
                        .getString("name"))
                .isEqualTo("srv");
    }

    @Test
    void legacyOnlyMethodsAreNotFoundWith404() {
        for (String method : new String[] {"initialize", "ping", "logging/setLevel", "resources/subscribe"}) {
            McpReply reply = handler.handle(new JsonRpcRequest(1, method, null), modern(null), false);

            assertThat(reply.status()).as(method).isEqualTo(404);
            assertThat(parse(reply.body()).getJsonObject("error").getInt("code"))
                    .isEqualTo(-32601);
        }
    }

    @Test
    void listResultsCarryResultType() {
        when(features.listTools(null, true))
                .thenReturn(Json.createObjectBuilder()
                        .add("tools", Json.createArrayBuilder())
                        .build());

        McpReply reply = handler.handle(new JsonRpcRequest(2, "tools/list", null), modern(null), true);

        assertThat(reply.isStream()).isFalse();
        assertThat(parse(reply.body()).getJsonObject("result").getString("resultType"))
                .isEqualTo("complete");
    }

    // --- SEP-2549 caching hints, required on every CacheableResult of the 2026-07-28 schema ---

    @Test
    void cacheableListResultsCarryTtlMsAndCacheScope() {
        when(features.listTools(null, true))
                .thenReturn(Json.createObjectBuilder()
                        .add("tools", Json.createArrayBuilder())
                        .build());
        when(features.listPrompts(null))
                .thenReturn(Json.createObjectBuilder()
                        .add("prompts", Json.createArrayBuilder())
                        .build());
        when(features.listResources(null))
                .thenReturn(Json.createObjectBuilder()
                        .add("resources", Json.createArrayBuilder())
                        .build());
        when(features.listResourceTemplates(null))
                .thenReturn(Json.createObjectBuilder()
                        .add("resourceTemplates", Json.createArrayBuilder())
                        .build());

        for (String method :
                new String[] {"tools/list", "prompts/list", "resources/list", "resources/templates/list"}) {
            McpReply reply = handler.handle(new JsonRpcRequest(2, method, null), modern(null), false);

            JsonObject result = parse(reply.body()).getJsonObject("result");
            assertThat(result.getJsonNumber("ttlMs").longValue()).as(method).isZero();
            assertThat(result.getString("cacheScope")).as(method).isEqualTo("public");
        }
    }

    @Test
    void readResourceResultCarriesTtlMsAndCacheScope() {
        when(features.readResource(eq(6), any(), any(), isNull()))
                .thenReturn(Json.createObjectBuilder()
                        .add("contents", Json.createArrayBuilder())
                        .build());
        JsonObject params = Json.createObjectBuilder().add("uri", "test://x").build();

        McpReply reply = handler.handle(new JsonRpcRequest(6, "resources/read", params), modern(null), false);

        JsonObject result = parse(reply.body()).getJsonObject("result");
        assertThat(result.getJsonNumber("ttlMs").longValue()).isZero();
        assertThat(result.getString("cacheScope")).isEqualTo("public");
    }

    @Test
    void cachingHintsComeFromTheServerConfiguration() {
        McpServerConfig config = McpServerConfig.builder()
                .cacheTtl(Duration.ofMinutes(5))
                .cacheScope("private")
                .build();
        McpServerConfigResolver resolver = new McpServerConfigResolver(config);
        McpMrtrSupport support = new McpMrtrSupport(resolver);
        McpModernProtocolHandler configured =
                new McpModernProtocolHandler(features, resolver, registry, support, store);
        when(features.listTools(null, true))
                .thenReturn(Json.createObjectBuilder()
                        .add("tools", Json.createArrayBuilder())
                        .build());

        McpReply reply = configured.handle(new JsonRpcRequest(2, "tools/list", null), modern(null), false);

        JsonObject result = parse(reply.body()).getJsonObject("result");
        assertThat(result.getJsonNumber("ttlMs").longValue()).isEqualTo(300_000L);
        assertThat(result.getString("cacheScope")).isEqualTo("private");
    }

    @Test
    void nonCacheableResultsDoNotCarryCachingHints() {
        when(features.callTool(eq(7), any(), any(), isNull()))
                .thenReturn(Json.createObjectBuilder()
                        .add("content", Json.createArrayBuilder())
                        .build());
        JsonObject params = Json.createObjectBuilder().add("name", "greet").build();

        McpReply reply = handler.handle(new JsonRpcRequest(7, "tools/call", params), modern(null), false);

        JsonObject result = parse(reply.body()).getJsonObject("result");
        assertThat(result).doesNotContainKey("ttlMs").doesNotContainKey("cacheScope");
    }

    @Test
    void toolCallWithoutStreamingIsJsonWithModernContext() {
        when(features.callTool(eq(3), any(), any(), isNull()))
                .thenReturn(Json.createObjectBuilder()
                        .add("content", Json.createArrayBuilder())
                        .build());
        JsonObject params = Json.createObjectBuilder().add("name", "greet").build();

        McpReply reply = handler.handle(new JsonRpcRequest(3, "tools/call", params), modern(null), true);

        assertThat(reply.isStream()).isFalse();
        ArgumentCaptor<McpRequestContext> ctx = ArgumentCaptor.forClass(McpRequestContext.class);
        verify(features).callTool(eq(3), any(), ctx.capture(), isNull());
        assertThat(ctx.getValue().isModern()).isTrue();
        assertThat(ctx.getValue().sessionId()).isNull();
    }

    @Test
    void toolCallWithLogLevelStreamsNotificationsThenResult() throws Exception {
        when(features.callTool(eq(4), any(), any(), isNull())).thenAnswer(invocation -> {
            McpRequestContext ctx = invocation.getArgument(2);
            ctx.channel()
                    .send(Json.createObjectBuilder()
                            .add("jsonrpc", "2.0")
                            .add("method", "notifications/message")
                            .build());
            return Json.createObjectBuilder()
                    .add("content", Json.createArrayBuilder())
                    .build();
        });
        JsonObject params = Json.createObjectBuilder().add("name", "greet").build();

        McpReply reply = handler.handle(new JsonRpcRequest(4, "tools/call", params), modern(McpLogLevel.info), true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        reply.stream().write(out);

        String sse = out.toString(StandardCharsets.UTF_8);
        assertThat(reply.isStream()).isTrue();
        assertThat(sse.indexOf("notifications/message")).isLessThan(sse.indexOf("\"resultType\":\"complete\""));
        assertThat(sse).startsWith("event: message\ndata: ");
    }

    @Test
    void protocolErrorsKeepTheirHttpStatusAndData() {
        when(features.callTool(eq(5), any(), any(), isNull()))
                .thenThrow(McpProtocolErrors.missingClientCapability(5, "elicitation"));
        JsonObject params = Json.createObjectBuilder().add("name", "ask").build();

        McpReply reply = handler.handle(new JsonRpcRequest(5, "tools/call", params), modern(null), false);

        assertThat(reply.status()).isEqualTo(400);
        JsonObject error = parse(reply.body()).getJsonObject("error");
        assertThat(error.getInt("code")).isEqualTo(-32021);
        assertThat(error.getJsonObject("data")
                        .getJsonObject("requiredCapabilities")
                        .containsKey("elicitation"))
                .isTrue();
    }

    @Test
    void clientRequesterEnforcesDeclaredCapabilities() {
        ArgumentCaptor<McpRequestContext> ctx = ArgumentCaptor.forClass(McpRequestContext.class);
        when(features.callTool(eq(6), any(), ctx.capture(), isNull())).thenReturn(JsonValue.EMPTY_JSON_OBJECT);
        JsonObject params = Json.createObjectBuilder().add("name", "ask").build();

        handler.handle(new JsonRpcRequest(6, "tools/call", params), modern(null, "sampling"), false);

        McpClientRequester requester = ctx.getValue().clientRequester();
        assertThat(requester.isModern()).isTrue();
        assertThat(requester.supports("sampling")).isTrue();
        assertThatThrownBy(() -> requester.requireCapability("elicitation"))
                .isInstanceOfSatisfying(
                        McpException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(-32021));
    }

    @Test
    void listenWithoutTheListenRouteIsAJsonRpcErrorNotAStream() {
        JsonObject params = Json.createObjectBuilder()
                .add("notifications", Json.createObjectBuilder().add("toolsListChanged", true))
                .build();

        McpReply reply = handler.handle(new JsonRpcRequest(9, "subscriptions/listen", params), modern(null), true);

        assertThat(reply.isStream()).isFalse();
        assertThat(reply.status()).isEqualTo(400);
        JsonObject error = parse(reply.body()).getJsonObject("error");
        assertThat(error.getInt("code")).isEqualTo(-32600);
        assertThat(error.getString("message")).contains("subscriptions/listen");
        assertThat(registry.size()).isZero();
    }

    @Test
    void runtimeExceptionOnJsonPathBecomesInternalError() {
        when(features.callTool(eq(90), any(), any(), isNull())).thenThrow(new IllegalStateException("boom"));
        JsonObject params = Json.createObjectBuilder().add("name", "greet").build();

        McpReply reply = handler.handle(new JsonRpcRequest(90, "tools/call", params), modern(null), false);

        assertThat(reply.isStream()).isFalse();
        assertThat(reply.status()).isEqualTo(200);
        JsonObject body = parse(reply.body());
        assertThat(body.getInt("id")).isEqualTo(90);
        assertThat(body.getJsonObject("error").getInt("code")).isEqualTo(-32603);
    }

    @Test
    void runtimeExceptionOnSsePathEndsTheStreamWithAJsonRpcError() throws Exception {
        when(features.callTool(eq(91), any(), any(), isNull())).thenThrow(new IllegalStateException("boom"));
        JsonObject params = Json.createObjectBuilder().add("name", "greet").build();

        McpReply reply = handler.handle(new JsonRpcRequest(91, "tools/call", params), modern(McpLogLevel.info), true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        reply.stream().write(out);

        assertThat(reply.isStream()).isTrue();
        String sse = out.toString(StandardCharsets.UTF_8).trim();
        String lastData = sse.substring(sse.lastIndexOf("data: ") + "data: ".length());
        JsonObject last = parse(lastData);
        assertThat(last.getInt("id")).isEqualTo(91);
        assertThat(last.getJsonObject("error").getInt("code")).isEqualTo(-32603);
    }

    @Test
    void malformedPromptArgumentsAreInvalidParams() {
        JsonObject params = Json.createObjectBuilder()
                .add("name", "summarize")
                .add("arguments", 5)
                .build();

        McpReply reply = handler.handle(new JsonRpcRequest(92, "prompts/get", params), modern(null), false);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(parse(reply.body()).getJsonObject("error").getInt("code")).isEqualTo(-32602);
        verify(features, never()).getPrompt(any(), any(), any(), any());
    }

    @Test
    void malformedCompletionRefIsInvalidParams() {
        JsonObject params = Json.createObjectBuilder()
                .add("ref", Json.createObjectBuilder().add("type", 7))
                .build();

        McpReply reply = handler.handle(new JsonRpcRequest(93, "completion/complete", params), modern(null), false);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(parse(reply.body()).getJsonObject("error").getInt("code")).isEqualTo(-32602);
    }

    @Test
    void inputRequiredSignalEscapingTheInvocationIsNotConvertedToAnError() {
        toolAsking(1);

        JsonObject result = call(94, callParams(null, null, null, 1)).getJsonObject("result");

        assertThat(result.getString("resultType")).isEqualTo("input_required");
    }

    /** Simulates a tool asking the client for input {@code count} times before answering. */
    private void toolAsking(int count) {
        when(features.callTool(any(), any(), any(), isNull())).thenAnswer(invocation -> {
            McpRequestContext ctx = invocation.getArgument(2);
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < count; i++) {
                ctx.clientRequester().requireCapability("elicitation");
                JsonObject answer = ctx.clientRequester()
                        .request("elicitation/create", Map.of("message", "Name " + i + "?"), Duration.ofSeconds(1));
                names.append(answer.getJsonObject("content").getString("name"));
            }
            return Json.createObjectBuilder()
                    .add(
                            "content",
                            Json.createArrayBuilder()
                                    .add(Json.createObjectBuilder()
                                            .add("type", "text")
                                            .add("text", "Hello " + names)))
                    .build();
        });
    }

    private static JsonObject callParams(String requestState, String key, String name, int x) {
        var params = Json.createObjectBuilder()
                .add("name", "ask")
                .add("arguments", Json.createObjectBuilder().add("x", x));
        if (requestState != null) {
            params.add("requestState", requestState);
        }
        if (key != null) {
            params.add(
                    "inputResponses",
                    Json.createObjectBuilder()
                            .add(
                                    key,
                                    Json.createObjectBuilder()
                                            .add("action", "accept")
                                            .add(
                                                    "content",
                                                    Json.createObjectBuilder().add("name", name))));
        }
        return params.build();
    }

    private JsonObject call(Object id, JsonObject params) {
        McpReply reply =
                handler.handle(new JsonRpcRequest(id, "tools/call", params), modern(null, "elicitation"), false);
        return parse(reply.body());
    }

    @Test
    void replayReturnsInputRequiredThenCompletesOnRetry() {
        toolAsking(1);

        JsonObject first = call(10, callParams(null, null, null, 1)).getJsonObject("result");
        assertThat(first.getString("resultType")).isEqualTo("input_required");
        JsonObject inputRequest = first.getJsonObject("inputRequests").getJsonObject("input-0");
        assertThat(inputRequest.getString("method")).isEqualTo("elicitation/create");
        assertThat(inputRequest.getJsonObject("params").getString("message")).isEqualTo("Name 0?");
        assertThat(first.getString("requestState")).isNotBlank();
        assertThat(first.getJsonObject("_meta").containsKey("io.modelcontextprotocol/serverInfo"))
                .isTrue();

        JsonObject second = call(11, callParams(first.getString("requestState"), "input-0", "Ada", 1))
                .getJsonObject("result");
        assertThat(second.getString("resultType")).isEqualTo("complete");
        assertThat(second.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("Hello Ada");
    }

    @Test
    void replayCarriesPreviousAnswersAcrossRounds() {
        toolAsking(2);

        String state1 = call(20, callParams(null, null, null, 1))
                .getJsonObject("result")
                .getString("requestState");
        JsonObject round2 = call(21, callParams(state1, "input-0", "Ada", 1)).getJsonObject("result");
        assertThat(round2.getString("resultType")).isEqualTo("input_required");
        assertThat(round2.getJsonObject("inputRequests").containsKey("input-1")).isTrue();

        JsonObject round3 = call(22, callParams(round2.getString("requestState"), "input-1", "Bob", 1))
                .getJsonObject("result");
        assertThat(round3.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("Hello AdaBob");
    }

    private static JsonObject elicitationAnswer(String name) {
        return Json.createObjectBuilder()
                .add("action", "accept")
                .add("content", Json.createObjectBuilder().add("name", name))
                .build();
    }

    private static JsonObject callParamsWithAnswers(String requestState, Map<String, String> answers) {
        var inputs = Json.createObjectBuilder();
        answers.forEach((key, name) -> inputs.add(key, elicitationAnswer(name)));
        var params = Json.createObjectBuilder()
                .add("name", "ask")
                .add("arguments", Json.createObjectBuilder().add("x", 1))
                .add("inputResponses", inputs);
        if (requestState != null) {
            params.add("requestState", requestState);
        }
        return params.build();
    }

    @Test
    void replayIgnoresInputResponsesWithoutRequestState() {
        toolAsking(1);

        JsonObject result = call(23, callParamsWithAnswers(null, Map.of("input-0", "Mallory")))
                .getJsonObject("result");

        assertThat(result.getString("resultType")).isEqualTo("input_required");
        assertThat(result.getJsonObject("inputRequests").containsKey("input-0")).isTrue();
    }

    @Test
    void replayIgnoresAnswersForKeysThatWereNotRequested() {
        toolAsking(2);
        String state1 = call(24, callParams(null, null, null, 1))
                .getJsonObject("result")
                .getString("requestState");

        // input-1 was never requested: it must not be accepted early
        JsonObject round2 = call(25, callParamsWithAnswers(state1, Map.of("input-0", "Ada", "input-1", "Mallory")))
                .getJsonObject("result");
        assertThat(round2.getString("resultType")).isEqualTo("input_required");
        assertThat(round2.getJsonObject("inputRequests").containsKey("input-1")).isTrue();

        JsonObject round3 = call(26, callParamsWithAnswers(round2.getString("requestState"), Map.of("input-1", "Bob")))
                .getJsonObject("result");
        assertThat(round3.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("Hello AdaBob");
    }

    @Test
    void replayNeverOverridesAnswersCarriedBySignedState() {
        toolAsking(2);
        String state1 = call(27, callParams(null, null, null, 1))
                .getJsonObject("result")
                .getString("requestState");
        String state2 = call(28, callParams(state1, "input-0", "Ada", 1))
                .getJsonObject("result")
                .getString("requestState");

        JsonObject round3 = call(29, callParamsWithAnswers(state2, Map.of("input-0", "Mallory", "input-1", "Bob")))
                .getJsonObject("result");

        assertThat(round3.getString("resultType")).isEqualTo("complete");
        assertThat(round3.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("Hello AdaBob");
    }

    @Test
    void replayWithoutAnswerAsksAgain() {
        toolAsking(1);
        String state = call(30, callParams(null, null, null, 1))
                .getJsonObject("result")
                .getString("requestState");

        JsonObject again = call(31, callParams(state, null, null, 1)).getJsonObject("result");

        assertThat(again.getString("resultType")).isEqualTo("input_required");
        assertThat(again.getJsonObject("inputRequests").containsKey("input-0")).isTrue();
    }

    @Test
    void replayRejectsStateReusedWithOtherArguments() {
        toolAsking(1);
        String state = call(40, callParams(null, null, null, 1))
                .getJsonObject("result")
                .getString("requestState");

        McpReply reply = handler.handle(
                new JsonRpcRequest(41, "tools/call", callParams(state, "input-0", "Ada", 2)),
                modern(null, "elicitation"),
                false);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(parse(reply.body()).getJsonObject("error").getInt("code")).isEqualTo(-32602);
    }

    @Test
    void replayFailsWhenClientLacksCapability() {
        toolAsking(1);

        McpReply reply = handler.handle(
                new JsonRpcRequest(50, "tools/call", callParams(null, null, null, 1)), modern(null), false);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(parse(reply.body()).getJsonObject("error").getInt("code")).isEqualTo(-32021);
    }

    private McpModernProtocolHandler continuationHandler(McpContinuationStore[] storeHolder) {
        McpServerConfigResolver resolver = new McpServerConfigResolver(McpServerConfig.builder()
                .mrtrMode(McpMrtrMode.CONTINUATION)
                .continuationTimeout(Duration.ofSeconds(5))
                .build());
        McpMrtrSupport support = new McpMrtrSupport(resolver);
        storeHolder[0] = new McpContinuationStore(support);
        return new McpModernProtocolHandler(features, resolver, registry, support, storeHolder[0]);
    }

    @Test
    void continuationResumesTheSameInvocation() {
        toolAsking(1);
        McpContinuationStore[] holder = new McpContinuationStore[1];
        McpModernProtocolHandler continuation = continuationHandler(holder);
        try {
            JsonObject first = parse(continuation
                            .handle(
                                    new JsonRpcRequest(60, "tools/call", callParams(null, null, null, 1)),
                                    modern(null, "elicitation"),
                                    false)
                            .body())
                    .getJsonObject("result");
            assertThat(first.getString("resultType")).isEqualTo("input_required");

            JsonObject second = parse(continuation
                            .handle(
                                    new JsonRpcRequest(
                                            61,
                                            "tools/call",
                                            callParams(first.getString("requestState"), "input-0", "Ada", 1)),
                                    modern(null, "elicitation"),
                                    false)
                            .body())
                    .getJsonObject("result");

            assertThat(second.getString("resultType")).isEqualTo("complete");
            assertThat(second.getJsonArray("content").getJsonObject(0).getString("text"))
                    .isEqualTo("Hello Ada");
            verify(features, times(1)).callTool(any(), any(), any(), isNull());
        } finally {
            holder[0].shutdown();
        }
    }

    @Test
    void continuationRetryWithoutAnswerRepeatsTheRequest() {
        toolAsking(1);
        McpContinuationStore[] holder = new McpContinuationStore[1];
        McpModernProtocolHandler continuation = continuationHandler(holder);
        try {
            String state = parse(continuation
                            .handle(
                                    new JsonRpcRequest(70, "tools/call", callParams(null, null, null, 1)),
                                    modern(null, "elicitation"),
                                    false)
                            .body())
                    .getJsonObject("result")
                    .getString("requestState");

            JsonObject again = parse(continuation
                            .handle(
                                    new JsonRpcRequest(71, "tools/call", callParams(state, null, null, 1)),
                                    modern(null, "elicitation"),
                                    false)
                            .body())
                    .getJsonObject("result");

            assertThat(again.getString("resultType")).isEqualTo("input_required");
            assertThat(again.getJsonObject("inputRequests").containsKey("input-0"))
                    .isTrue();
        } finally {
            holder[0].shutdown();
        }
    }

    @Test
    void continuationWithUnknownIdIsRejected() {
        McpContinuationStore[] holder = new McpContinuationStore[1];
        McpModernProtocolHandler continuation = continuationHandler(holder);
        try {
            McpRequestStateCodec codec = continuation.mrtrSupport().codec();
            String digest = McpMrtrSupport.argumentsDigest("tools/call", callParams(null, null, null, 1));
            String token = codec.encode(new McpRequestStateCodec.State(
                    "tools/call", "ask", digest, codec.expiresAt(Duration.ofMinutes(1)), null, "missing"));

            McpReply reply = continuation.handle(
                    new JsonRpcRequest(80, "tools/call", callParams(token, null, null, 1)),
                    modern(null, "elicitation"),
                    false);

            assertThat(reply.status()).isEqualTo(400);
            JsonObject error = parse(reply.body()).getJsonObject("error");
            assertThat(error.getInt("code")).isEqualTo(-32602);
            assertThat(error.getString("message")).contains("Unknown or expired requestState");
        } finally {
            holder[0].shutdown();
        }
    }
}
