package dev.langchain4j.cdi.mcp.integrationtests;

import static dev.langchain4j.cdi.mcp.integrationtests.McpTestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reusable MCP integration test scenarios. Each public method exercises one server-side behaviour and is named after
 * the corresponding {@code @Test} method in the test classes.
 *
 * <p>Test classes that cannot use inheritance (Arquillian on WildFly/OpenLiberty, Helidon) delegate their {@code @Test}
 * methods here so that the assertion logic lives in a single place. {@link AbstractMcpIntegrationTest} also delegates
 * here.
 */
@SuppressWarnings("java:S112")
public final class McpIntegrationTestScenarios {

    private final McpHttpTransport transport;

    public McpIntegrationTestScenarios(McpHttpTransport transport) {
        this.transport = transport;
    }

    // ---- helpers (also usable by test classes that need ad-hoc requests) ----

    public String initializeSession() {
        McpHttpResponse response = transport.post("/mcp", McpTestRequests.initializeRequest(1), Map.of());
        JsonRpcAssertions.assertJsonRpcSuccess(response, 1);
        String sessionId = response.header(MCP_SESSION_ID);
        assertThat(sessionId).as(MCP_SESSION_ID + " header").isNotNull().isNotBlank();
        return sessionId;
    }

    public McpHttpResponse postMcp(String sessionId, String body) {
        return transport.post("/mcp", body, Map.of(MCP_SESSION_ID, sessionId));
    }

    public McpClient buildClient() {
        return buildClient(null);
    }

    public McpClient buildClient(String protocolVersion) {
        DefaultMcpClient.Builder builder = DefaultMcpClient.builder()
                .transport(StreamableHttpMcpTransport.builder()
                        .url(transport.baseUrl() + "/mcp")
                        .build());
        if (protocolVersion != null) {
            builder.protocolVersion(protocolVersion);
        }
        return builder.build();
    }

    private McpHttpResponse postModern(Object id, String method, String name, String paramsJson, String caps) {
        return transport.post(
                "/mcp",
                McpModernTestRequests.body(id, method, paramsJson, caps),
                McpModernTestRequests.headers(method, name));
    }

    private McpHttpResponse postDesignatedCall(int id, String... paramHeaders) {
        return transport.post(
                "/mcp",
                McpModernTestRequests.designatedCallBody(id),
                McpModernTestRequests.designatedCallHeaders(paramHeaders));
    }

    // ---- Tools via MCP Client ----

    public void shouldListToolsViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            List<ToolSpecification> tools = client.listTools();
            assertThat(tools)
                    .hasSizeGreaterThanOrEqualTo(2)
                    .extracting(ToolSpecification::name)
                    .contains(GET_WEATHER, GREET);
        }
    }

    public void shouldCallToolViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(GET_WEATHER)
                    .arguments("{\"city\":\"Paris\",\"unit\":\"celsius\"}")
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).contains("Paris");
        }
    }

    public void shouldCallToolWithOptionalParamOmitted() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(GREET)
                    .arguments("{\"name\":\"Alice\"}")
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).isEqualTo("Hello, Alice!");
        }
    }

    public void shouldCallToolWithOptionalParamProvided() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(GREET)
                    .arguments("{\"name\":\"Bob\",\"prefix\":\"Bonjour\"}")
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).isEqualTo("Bonjour, Bob!");
        }
    }

    public void shouldHaveOptionalParameterInToolSchema() throws Exception {
        try (McpClient client = buildClient()) {
            List<ToolSpecification> tools = client.listTools();
            ToolSpecification greet = tools.stream()
                    .filter(t -> t.name().equals(GREET))
                    .findFirst()
                    .orElseThrow();

            assertThat(greet.description()).isEqualTo("Greet someone by name");
            assertThat(greet.parameters().required()).contains("name").doesNotContain("prefix");
        }
    }

    public void shouldCallToolWithUnknownNameViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("nonExistentTool")
                    .arguments("{}")
                    .build();
            assertThatThrownBy(() -> client.executeTool(request)).isInstanceOf(Exception.class);
        }
    }

    public void shouldListToolsMultipleTimes() throws Exception {
        try (McpClient client = buildClient()) {
            List<ToolSpecification> tools1 = client.listTools();
            List<ToolSpecification> tools2 = client.listTools();
            assertThat(tools1).hasSizeGreaterThanOrEqualTo(2);
            assertThat(tools2)
                    .hasSizeGreaterThanOrEqualTo(2)
                    .extracting(ToolSpecification::name)
                    .containsExactlyInAnyOrderElementsOf(
                            tools1.stream().map(ToolSpecification::name).toList());
        }
    }

    public void shouldDetectModernProtocolAutomatically() throws Exception {
        try (McpClient client = buildClient()) {
            client.listTools();
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isTrue();
        }
    }

    public void shouldCallToolViaLegacyClient() throws Exception {
        try (McpClient client = buildClient(LEGACY_CLIENT_VERSION)) {
            ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                    .name(GREET)
                    .arguments("{\"name\":\"Ada\"}")
                    .build());
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isFalse();
            assertThat(result.resultText()).contains("Hello, Ada!");
        }
    }

    public void shouldCallToolViaModernClient() throws Exception {
        try (McpClient client = buildClient(MODERN_VERSION)) {
            ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                    .name(GREET)
                    .arguments("{\"name\":\"Ada\"}")
                    .build());
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isTrue();
            assertThat(result.resultText()).contains("Hello, Ada!");
        }
    }

    public void shouldListToolsViaLegacyClient() throws Exception {
        try (McpClient client = buildClient(LEGACY_CLIENT_VERSION)) {
            assertThat(client.listTools()).extracting(ToolSpecification::name).contains(GET_WEATHER, GREET, ASK_NAME);
        }
    }

    // ---- Session & HTTP ----

    public void shouldInitializeSessionSuccessfully() {
        String sessionId = initializeSession();
        assertThat(sessionId).isNotNull().isNotBlank();
    }

    public void shouldSendInitializedNotification() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.initializedNotification());
        JsonRpcAssertions.assertNotificationAccepted(response);
    }

    public void shouldRejectInitializeSentAsNotification() {
        McpHttpResponse response = transport.post("/mcp", McpTestRequests.initializeNotification(), Map.of());

        assertThat(response.header(MCP_SESSION_ID))
                .as("no session should be created for an id-less initialize")
                .isNull();
        JsonRpcAssertions.assertJsonRpcError(
                response, null, -32600, "initialize must be a request, not a notification");

        McpHttpResponse followUp = transport.post("/mcp", McpTestRequests.toolsListRequest(2), Map.of());
        assertThat(followUp.statusCode()).as("no usable session should remain").isEqualTo(400);
    }

    public void shouldHandlePing() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.pingRequest(99));
        JsonRpcAssertions.assertJsonRpcSuccess(response, 99);
    }

    public void shouldDeleteSession() {
        String sessionId = initializeSession();
        McpHttpResponse response = transport.delete("/mcp", Map.of(MCP_SESSION_ID, sessionId));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    public void shouldReturnCleanJsonForToolCall() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(
                sessionId,
                McpTestRequests.toolsCallRequest(10, GET_WEATHER, "{\"city\":\"Paris\",\"unit\":\"celsius\"}"));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 10);
        JsonArray content = result.getJsonArray("content");
        assertThat(content).isNotNull().isNotEmpty();

        JsonObject firstContent = content.getJsonObject(0);
        assertThat(firstContent.getString("text")).contains("Paris");
        assertThat(firstContent).doesNotContainKey("data").doesNotContainKey("mimeType");
    }

    public void shouldReturnErrorForUnknownMethod() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.unknownMethodRequest("5", "unknown/method"));
        JsonRpcAssertions.assertJsonRpcError(response, "5", -32601, "Unknown method");
    }

    public void shouldReturnParseErrorForMalformedBody() {
        McpHttpResponse response = transport.post("/mcp", "{not json", Map.of());

        assertThat(response.statusCode()).isEqualTo(400);
        JsonObject json;
        try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
            json = reader.readObject();
        }
        assertThat(json.getJsonObject("error").getInt("code")).isEqualTo(-32700);
    }

    // ---- Resources ----

    public void shouldListResources() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesListRequest(20));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 20);
        JsonArray resources = result.getJsonArray(RESOURCES);
        assertThat(resources).isNotNull();

        List<String> uris =
                resources.stream().map(v -> v.asJsonObject().getString("uri")).toList();
        assertThat(uris).contains(CONFIG_APP, "data://status");

        List<String> names =
                resources.stream().map(v -> v.asJsonObject().getString("name")).toList();
        assertThat(names).contains("Application Config");
    }

    public void shouldReadResource() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesReadRequest(21, CONFIG_APP));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 21);
        JsonArray contents = result.getJsonArray("contents");
        assertThat(contents).isNotNull().isNotEmpty();
        assertThat(contents.getJsonObject(0).getString("uri")).isEqualTo(CONFIG_APP);
        assertThat(contents.getJsonObject(0).getString("text")).contains("version");
    }

    public void shouldReturnErrorForUnknownResource() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesReadRequest(22, "unknown://x"));
        JsonRpcAssertions.assertJsonRpcError(response, 22, -32602, "Resource not found");
    }

    // ---- Prompts ----

    public void shouldListPrompts() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.promptsListRequest(30));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 30);
        JsonArray prompts = result.getJsonArray("prompts");
        assertThat(prompts).isNotNull();

        List<String> names =
                prompts.stream().map(v -> v.asJsonObject().getString("name")).toList();
        assertThat(names).contains(SUMMARIZE);
    }

    public void shouldGetPrompt() {
        String sessionId = initializeSession();
        McpHttpResponse response =
                postMcp(sessionId, McpTestRequests.promptsGetRequest(31, SUMMARIZE, "{\"text\":\"Hello world\"}"));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 31);
        JsonArray messages = result.getJsonArray("messages");
        assertThat(messages).isNotNull().isNotEmpty();

        JsonObject firstMessage = messages.getJsonObject(0);
        assertThat(firstMessage).containsKey("content");
        assertThat(firstMessage.getJsonObject("content").getString("text")).contains("Hello world");
    }

    public void shouldReturnErrorForUnknownPrompt() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.promptsGetRequest(32, "nonexistent"));
        JsonRpcAssertions.assertJsonRpcError(response, 32, -32602, "Prompt not found");
    }

    // ---- Logging ----

    public void shouldSetLogLevel() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.loggingSetLevelRequest(40, "warning"));
        JsonRpcAssertions.assertJsonRpcSuccess(response, 40);
    }

    public void shouldReturnErrorForInvalidLogLevel() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.loggingSetLevelRequest(41, "banana"));
        JsonRpcAssertions.assertJsonRpcError(response, 41, -32602, "Invalid log level");
    }

    // ---- Resource Subscriptions ----

    public void shouldSubscribeToResource() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesSubscribeRequest(60, CONFIG_APP));
        JsonRpcAssertions.assertJsonRpcSuccess(response, 60);
    }

    public void shouldUnsubscribeFromResource() {
        String sessionId = initializeSession();
        postMcp(sessionId, McpTestRequests.resourcesSubscribeRequest(61, CONFIG_APP));

        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesUnsubscribeRequest(62, CONFIG_APP));
        JsonRpcAssertions.assertJsonRpcSuccess(response, 62);
    }

    // ---- Resource Templates ----

    public void shouldListResourceTemplates() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesTemplatesListRequest(63));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 63);
        assertThat(result).containsKey("resourceTemplates");
    }

    // ---- Completion ----

    public void shouldReturnEmptyCompletionForUnknownRef() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(
                sessionId, McpTestRequests.completionCompleteRequest(64, "ref/prompt", "nonexistent", "text", ""));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 64);
        assertThat(result).containsKey("completion");
        assertThat(result.getJsonObject("completion").getJsonArray("values")).isNotNull();
    }

    // ---- Notifications ----

    public void shouldAcknowledgeCancellation() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.cancelledNotification("abc", "timeout"));
        JsonRpcAssertions.assertNotificationAccepted(response);
    }

    public void shouldAcceptRootsListChangedNotification() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.rootsListChangedNotification());
        JsonRpcAssertions.assertNotificationAccepted(response);
    }

    public void shouldAcceptClientJsonRpcResponse() {
        String sessionId = initializeSession();
        McpHttpResponse response =
                postMcp(sessionId, McpTestRequests.clientJsonRpcResponse("server-999", "{\"roots\":[]}"));
        JsonRpcAssertions.assertNotificationAccepted(response);
    }

    public void shouldAcceptAnyNotificationShapedMessage() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.pingNotification());
        JsonRpcAssertions.assertNotificationAccepted(response);
    }

    // ---- Capabilities ----

    public void shouldDeclareAllCapabilities() {
        McpHttpResponse response = transport.post("/mcp", McpTestRequests.initializeRequest(50), Map.of());

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 50);
        assertThat(result).containsKey("capabilities");

        JsonObject capabilities = result.getJsonObject("capabilities");
        assertThat(capabilities)
                .containsKey("tools")
                .containsKey(RESOURCES)
                .containsKey("prompts")
                .containsKey("logging");

        JsonObject resourcesCap = capabilities.getJsonObject(RESOURCES);
        assertThat(resourcesCap).containsKey("subscribe");
    }

    // ---- Negative session tests ----

    public void shouldRejectRequestWithInvalidSessionId() {
        McpHttpResponse response = postMcp("bogus-session-id-12345", McpTestRequests.toolsListRequest(70));
        JsonObject error = JsonRpcAssertions.assertHttpJsonRpcError(response, 404, 70, -32001);
        assertThat(error.getString("message")).contains("Invalid or missing Mcp-Session-Id");
    }

    public void shouldRejectForeignOrigin() {
        McpHttpResponse response =
                transport.post("/mcp", McpTestRequests.initializeRequest(71), Map.of("Origin", "https://evil.example"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    public void shouldAcceptLocalhostOrigin() {
        McpHttpResponse response = transport.post(
                "/mcp", McpTestRequests.initializeRequest(72), Map.of("Origin", "http://localhost:3000"));

        JsonRpcAssertions.assertJsonRpcSuccess(response, 72);
    }

    // ---- Modern protocol (2026-07-28) ----

    public void shouldDiscoverSupportedVersions() {
        JsonObject result =
                JsonRpcAssertions.assertJsonRpcSuccess(postModern(100, "server/discover", null, "", "{}"), 100);

        assertThat(result.getString("resultType")).isEqualTo("complete");
        assertThat(result.getJsonArray("supportedVersions").getValuesAs(JsonString::getString))
                .containsExactly(MODERN_VERSION, "2025-03-26");
        assertThat(result.getJsonObject("capabilities")).containsKey("tools");
    }

    public void shouldServeModernRequestsWithoutSession() {
        McpHttpResponse response = postModern(101, "tools/list", null, "", "{}");

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 101);
        assertThat(response.header(MCP_SESSION_ID)).isNull();
        assertThat(result.getString("resultType")).isEqualTo("complete");
        assertThat(result.getJsonArray("tools").getValuesAs(JsonObject.class))
                .extracting(t -> t.getString("name"))
                .contains(GREET, ASK_NAME);
    }

    public void shouldPublishIconsInTheModernEraOnly() {
        JsonObject modern = JsonRpcAssertions.assertJsonRpcSuccess(postModern(120, "tools/list", null, "", "{}"), 120);
        JsonObject modernTool = modern.getJsonArray("tools").getValuesAs(JsonObject.class).stream()
                .filter(t -> IconedTool.ICONED.equals(t.getString("name")))
                .findFirst()
                .orElseThrow();
        JsonObject icon = modernTool.getJsonArray("icons").getJsonObject(0);
        assertThat(icon.getString("src")).isEqualTo(IconedTool.ICON_SRC);
        assertThat(icon.getString("mimeType")).isEqualTo("image/png");
        assertThat(icon.getJsonArray("sizes").getValuesAs(JsonString::getString))
                .containsExactly("48x48");
        assertThat(icon.getString("theme")).isEqualTo("light");

        String sessionId = initializeSession();
        JsonObject legacy =
                JsonRpcAssertions.assertJsonRpcSuccess(postMcp(sessionId, McpTestRequests.toolsListRequest(121)), 121);
        JsonObject legacyTool = legacy.getJsonArray("tools").getValuesAs(JsonObject.class).stream()
                .filter(t -> IconedTool.ICONED.equals(t.getString("name")))
                .findFirst()
                .orElseThrow();
        assertThat(legacyTool).doesNotContainKey("icons");
    }

    public void shouldCallToolWithModernRequest() {
        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(
                postModern(102, "tools/call", GREET, "\"name\":\"greet\",\"arguments\":{\"name\":\"Ada\"}", "{}"), 102);

        assertThat(result.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("Hello, Ada!");
    }

    public void shouldRejectMismatchedNameHeader() {
        McpHttpResponse response =
                postModern(103, "tools/call", "other", "\"name\":\"greet\",\"arguments\":{\"name\":\"Ada\"}", "{}");

        JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 103, -32020);
    }

    // ---- SEP-2243 request half: Mcp-Param-<designation> validated against the body ----

    public void shouldAcceptMirroredParamHeadersMatchingTheBody() {
        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(
                postDesignatedCall(
                        130,
                        "Mcp-Param-" + HeaderParamTool.TENANT_HEADER,
                        "acme",
                        "Mcp-Param-" + HeaderParamTool.ATTEMPT_HEADER,
                        "7"),
                130);

        assertThat(result.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("tenant=acme, attempt=7");
    }

    public void shouldMatchParamHeaderNamesCaseInsensitively() {
        JsonRpcAssertions.assertJsonRpcSuccess(
                postDesignatedCall(131, "mcp-param-tenant-id", "acme", "MCP-PARAM-ATTEMPT", "7"), 131);
    }

    public void shouldAcceptABase64WrappedParamHeader() {
        JsonRpcAssertions.assertJsonRpcSuccess(
                postDesignatedCall(
                        132,
                        "Mcp-Param-" + HeaderParamTool.TENANT_HEADER,
                        "=?base64?YWNtZQ==?=",
                        "Mcp-Param-" + HeaderParamTool.ATTEMPT_HEADER,
                        "7"),
                132);
    }

    public void shouldRejectAParamHeaderDisagreeingWithTheBody() {
        McpHttpResponse response = postDesignatedCall(
                133,
                "Mcp-Param-" + HeaderParamTool.TENANT_HEADER,
                "evilcorp",
                "Mcp-Param-" + HeaderParamTool.ATTEMPT_HEADER,
                "7");

        JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 133, -32020);
    }

    public void shouldRejectAnOmittedParamHeaderWhenTheBodyCarriesTheValue() {
        McpHttpResponse response = postDesignatedCall(134, "Mcp-Param-" + HeaderParamTool.ATTEMPT_HEADER, "7");

        JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 134, -32020);
    }

    public void shouldRejectAnIntegerParamHeaderThatIsNotTheDecimalBodyValue() {
        McpHttpResponse response = postDesignatedCall(
                135,
                "Mcp-Param-" + HeaderParamTool.TENANT_HEADER,
                "acme",
                "Mcp-Param-" + HeaderParamTool.ATTEMPT_HEADER,
                "8");

        JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 135, -32020);
    }

    public void shouldRejectMalformedBase64ParamHeaderWith400NotWith500() {
        for (String malformed : McpModernTestRequests.malformedBase64Values()) {
            McpHttpResponse response = postDesignatedCall(
                    136,
                    "Mcp-Param-" + HeaderParamTool.TENANT_HEADER,
                    malformed,
                    "Mcp-Param-" + HeaderParamTool.ATTEMPT_HEADER,
                    "7");

            assertThat(response.statusCode()).as(malformed).isEqualTo(400);
            JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 136, -32020);
        }
    }

    public void shouldIgnoreParamHeadersInTheLegacyEra() {
        String sessionId = initializeSession();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":137,\"method\":\"tools/call\",\"params\":{\"name\":\""
                + HeaderParamTool.TENANT_ECHO + "\",\"arguments\":{\"tenant\":\"acme\",\"attempt\":7}}}";
        Map<String, String> headers = new HashMap<>();
        headers.put(MCP_SESSION_ID, sessionId);
        headers.put("Mcp-Param-" + HeaderParamTool.TENANT_HEADER, "=?base64?ZXZpbA?=");

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(transport.post("/mcp", body, headers), 137);

        assertThat(result.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("tenant=acme, attempt=7");
    }

    public void shouldRejectUnsupportedProtocolVersion() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":104,\"method\":\"tools/list\",\"params\":{"
                + McpModernTestRequests.meta("2099-01-01", "{}") + "}}";
        Map<String, String> headers = new HashMap<>(McpModernTestRequests.headers("tools/list", null));
        headers.put("MCP-Protocol-Version", "2099-01-01");

        JsonObject error =
                JsonRpcAssertions.assertHttpJsonRpcError(transport.post("/mcp", body, headers), 400, 104, -32022);

        assertThat(error.getJsonObject("data").getJsonArray("supported").getString(0))
                .isEqualTo(MODERN_VERSION);
    }

    public void shouldReturn404ForLegacyOnlyMethodInModernEra() {
        JsonRpcAssertions.assertHttpJsonRpcError(postModern(105, "ping", null, "", "{}"), 404, 105, -32601);
    }

    public void shouldAcceptModernNotificationWith202() {
        McpHttpResponse response = postModern(null, "notifications/cancelled", null, "\"requestId\":1", "{}");

        assertThat(response.statusCode()).isEqualTo(202);
    }

    public void shouldRoundTripElicitationWithMrtr() {
        String caps = "{\"elicitation\":{}}";
        JsonObject first = JsonRpcAssertions.assertJsonRpcSuccess(
                postModern(106, "tools/call", ASK_NAME, "\"name\":\"askName\",\"arguments\":{}", caps), 106);
        assertThat(first.getString("resultType")).isEqualTo("input_required");
        JsonObject inputRequest = first.getJsonObject("inputRequests").getJsonObject("input-0");
        assertThat(inputRequest.getString("method")).isEqualTo("elicitation/create");
        assertThat(inputRequest.getJsonObject("params").getString("mode")).isEqualTo("form");

        String retryParams =
                "\"name\":\"askName\",\"arguments\":{},\"requestState\":\"" + first.getString("requestState")
                        + "\",\"inputResponses\":{\"input-0\":{\"action\":\"accept\",\"content\":{\"name\":\"Ada\"}}}";
        JsonObject second =
                JsonRpcAssertions.assertJsonRpcSuccess(postModern(107, "tools/call", ASK_NAME, retryParams, caps), 107);

        assertThat(second.getString("resultType")).isEqualTo("complete");
        assertThat(second.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("Hello, Ada!");
    }

    public void shouldRequireDeclaredElicitationCapability() {
        McpHttpResponse response =
                postModern(108, "tools/call", ASK_NAME, "\"name\":\"askName\",\"arguments\":{}", "{}");

        JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 108, -32021);
    }

    public void shouldAcknowledgeListenSubscription() throws Exception {
        String body = McpModernTestRequests.body(
                109, "subscriptions/listen", "\"notifications\":{\"toolsListChanged\":true}", "{}");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(transport.baseUrl() + "/mcp"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        McpModernTestRequests.headers("subscriptions/listen", null).forEach(request::header);
        request.setHeader("Accept", "text/event-stream");

        HttpResponse<Stream<String>> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(request.build(), HttpResponse.BodyHandlers.ofLines());

        try (Stream<String> lines = response.body()) {
            String firstEvent =
                    lines.filter(line -> line.startsWith("data:")).findFirst().orElseThrow();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(firstEvent)
                    .contains("notifications/subscriptions/acknowledged")
                    .contains("\"toolsListChanged\":true");
        }
    }

    public void shouldRejectListenWithMismatchedProtocolVersionHeader() {
        String body = McpModernTestRequests.body(
                110, "subscriptions/listen", "\"notifications\":{\"toolsListChanged\":true}", "{}");
        Map<String, String> headers = new HashMap<>(McpModernTestRequests.headers("subscriptions/listen", null));
        headers.put("MCP-Protocol-Version", "2099-01-01");
        headers.put("Accept", "application/json, text/event-stream");

        JsonRpcAssertions.assertHttpJsonRpcError(transport.post("/mcp", body, headers), 400, 110, -32020);
    }
}
