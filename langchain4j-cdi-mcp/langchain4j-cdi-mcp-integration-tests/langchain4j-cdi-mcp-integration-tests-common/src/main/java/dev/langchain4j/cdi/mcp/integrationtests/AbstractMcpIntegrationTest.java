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
import org.junit.jupiter.api.Test;

/** Base class for MCP server integration tests. */
@SuppressWarnings("java:S112")
public abstract class AbstractMcpIntegrationTest {

    /** JSON key for the {@code content} array in MCP responses. */
    public static final String CONTENT = "content";

    /** Creates a new instance. */
    public AbstractMcpIntegrationTest() {}

    /**
     * Returns the HTTP transport used to communicate with the MCP endpoint.
     *
     * @return the transport
     */
    protected abstract McpHttpTransport transport();

    // --- Tools via MCP Client ---

    @Test
    void shouldListToolsViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            List<ToolSpecification> tools = client.listTools();
            assertThat(tools)
                    .hasSizeGreaterThanOrEqualTo(2)
                    .extracting(ToolSpecification::name)
                    .contains(GET_WEATHER, GREET);
        }
    }

    @Test
    void shouldCallToolViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(GET_WEATHER)
                    .arguments("{\"city\":\"Paris\",\"unit\":\"celsius\"}")
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).contains("Paris");
        }
    }

    @Test
    void shouldCallToolWithOptionalParamOmitted() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(GREET)
                    .arguments("{\"name\":\"Alice\"}")
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).isEqualTo("Hello, Alice!");
        }
    }

    @Test
    void shouldCallToolWithOptionalParamProvided() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(GREET)
                    .arguments("{\"name\":\"Bob\",\"prefix\":\"Bonjour\"}")
                    .build();
            ToolExecutionResult result = client.executeTool(request);
            assertThat(result.resultText()).isEqualTo("Bonjour, Bob!");
        }
    }

    @Test
    void shouldHaveOptionalParameterInToolSchema() throws Exception {
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

    @Test
    void shouldCallToolWithUnknownNameViaMcpClient() throws Exception {
        try (McpClient client = buildClient()) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("nonExistentTool")
                    .arguments("{}")
                    .build();
            assertThatThrownBy(() -> client.executeTool(request)).isInstanceOf(Exception.class);
        }
    }

    @Test
    void shouldListToolsMultipleTimes() throws Exception {
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

    @Test
    void shouldDetectModernProtocolAutomatically() throws Exception {
        try (McpClient client = buildClient()) {
            client.listTools();
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isTrue();
        }
    }

    @Test
    void shouldCallToolViaLegacyClient() throws Exception {
        try (McpClient client = buildClient(LEGACY_CLIENT_VERSION)) {
            ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                    .name(GREET)
                    .arguments("{\"name\":\"Ada\"}")
                    .build());
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isFalse();
            assertThat(result.resultText()).contains("Hello, Ada!");
        }
    }

    @Test
    void shouldCallToolViaModernClient() throws Exception {
        try (McpClient client = buildClient(MODERN_VERSION)) {
            ToolExecutionResult result = client.executeTool(ToolExecutionRequest.builder()
                    .name(GREET)
                    .arguments("{\"name\":\"Ada\"}")
                    .build());
            assertThat(((DefaultMcpClient) client).isModernProtocol()).isTrue();
            assertThat(result.resultText()).contains("Hello, Ada!");
        }
    }

    @Test
    void shouldListToolsViaLegacyClient() throws Exception {
        try (McpClient client = buildClient(LEGACY_CLIENT_VERSION)) {
            assertThat(client.listTools()).extracting(ToolSpecification::name).contains(GET_WEATHER, GREET, ASK_NAME);
        }
    }

    // --- Session & HTTP ---

    @Test
    void shouldInitializeSessionSuccessfully() {
        String sessionId = initializeSession();
        assertThat(sessionId).isNotNull().isNotBlank();
    }

    @Test
    void shouldSendInitializedNotification() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.initializedNotification());
        JsonRpcAssertions.assertNotificationAccepted(response);
    }

    @Test
    void shouldHandlePing() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.pingRequest(99));
        JsonRpcAssertions.assertJsonRpcSuccess(response, 99);
    }

    @Test
    void shouldDeleteSession() {
        String sessionId = initializeSession();
        McpHttpResponse response = transport().delete("/mcp", Map.of(MCP_SESSION_ID, sessionId));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void shouldReturnCleanJsonForToolCall() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(
                sessionId,
                McpTestRequests.toolsCallRequest(10, GET_WEATHER, "{\"city\":\"Paris\",\"unit\":\"celsius\"}"));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 10);
        JsonArray content = result.getJsonArray(CONTENT);
        assertThat(content).isNotNull().isNotEmpty();

        JsonObject firstContent = content.getJsonObject(0);
        assertThat(firstContent.getString("text")).contains("Paris");
        assertThat(firstContent).doesNotContainKey("data").doesNotContainKey("mimeType");
    }

    @Test
    void shouldReturnErrorForUnknownMethod() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.unknownMethodRequest("5", "unknown/method"));
        JsonRpcAssertions.assertJsonRpcError(response, "5", -32601, "Unknown method");
    }

    @Test
    void shouldReturnParseErrorForMalformedBody() {
        McpHttpResponse response = transport().post("/mcp", "{not json", Map.of());

        assertThat(response.statusCode()).isEqualTo(400);
        JsonObject json;
        try (JsonReader reader = Json.createReader(new StringReader(response.body()))) {
            json = reader.readObject();
        }
        assertThat(json.getJsonObject("error").getInt("code")).isEqualTo(-32700);
    }

    // --- Resources ---

    @Test
    void shouldListResources() {
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

    @Test
    void shouldReadResource() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesReadRequest(21, CONFIG_APP));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 21);
        JsonArray contents = result.getJsonArray("contents");
        assertThat(contents).isNotNull().isNotEmpty();
        assertThat(contents.getJsonObject(0).getString("uri")).isEqualTo(CONFIG_APP);
        assertThat(contents.getJsonObject(0).getString("text")).contains("version");
    }

    @Test
    void shouldReturnErrorForUnknownResource() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesReadRequest(22, "unknown://x"));
        JsonRpcAssertions.assertJsonRpcError(response, 22, -32602, "Resource not found");
    }

    // --- Prompts ---

    @Test
    void shouldListPrompts() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.promptsListRequest(30));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 30);
        JsonArray prompts = result.getJsonArray("prompts");
        assertThat(prompts).isNotNull();

        List<String> names =
                prompts.stream().map(v -> v.asJsonObject().getString("name")).toList();
        assertThat(names).contains(SUMMARIZE);
    }

    @Test
    void shouldGetPrompt() {
        String sessionId = initializeSession();
        McpHttpResponse response =
                postMcp(sessionId, McpTestRequests.promptsGetRequest(31, SUMMARIZE, "{\"text\":\"Hello world\"}"));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 31);
        JsonArray messages = result.getJsonArray("messages");
        assertThat(messages).isNotNull().isNotEmpty();

        JsonObject firstMessage = messages.getJsonObject(0);
        assertThat(firstMessage).containsKey(CONTENT);
        assertThat(firstMessage.getJsonObject(CONTENT).getString("text")).contains("Hello world");
    }

    @Test
    void shouldReturnErrorForUnknownPrompt() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.promptsGetRequest(32, "nonexistent"));
        JsonRpcAssertions.assertJsonRpcError(response, 32, -32602, "Prompt not found");
    }

    // --- Logging ---

    @Test
    void shouldSetLogLevel() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.loggingSetLevelRequest(40, "warning"));
        JsonRpcAssertions.assertJsonRpcSuccess(response, 40);
    }

    @Test
    void shouldReturnErrorForInvalidLogLevel() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.loggingSetLevelRequest(41, "banana"));
        JsonRpcAssertions.assertJsonRpcError(response, 41, -32602, "Invalid log level");
    }

    // --- Resource Subscriptions ---

    @Test
    void shouldSubscribeToResource() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesSubscribeRequest(60, CONFIG_APP));
        JsonRpcAssertions.assertJsonRpcSuccess(response, 60);
    }

    @Test
    void shouldUnsubscribeFromResource() {
        String sessionId = initializeSession();
        postMcp(sessionId, McpTestRequests.resourcesSubscribeRequest(61, CONFIG_APP));

        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesUnsubscribeRequest(62, CONFIG_APP));
        JsonRpcAssertions.assertJsonRpcSuccess(response, 62);
    }

    // --- Resource Templates ---

    @Test
    void shouldListResourceTemplates() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.resourcesTemplatesListRequest(63));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 63);
        assertThat(result).containsKey("resourceTemplates");
    }

    // --- Completion ---

    @Test
    void shouldReturnEmptyCompletionForUnknownRef() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(
                sessionId, McpTestRequests.completionCompleteRequest(64, "ref/prompt", "nonexistent", "text", ""));

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 64);
        assertThat(result).containsKey("completion");
        assertThat(result.getJsonObject("completion").getJsonArray("values")).isNotNull();
    }

    // --- Notifications ---

    @Test
    void shouldAcknowledgeCancellation() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.cancelledNotification("abc", "timeout"));
        JsonRpcAssertions.assertNotificationAccepted(response);
    }

    @Test
    void shouldAcceptClientJsonRpcResponse() {
        String sessionId = initializeSession();
        McpHttpResponse response =
                postMcp(sessionId, McpTestRequests.clientJsonRpcResponse("server-999", "{\"roots\":[]}"));
        JsonRpcAssertions.assertNotificationAccepted(response);
    }

    @Test
    void shouldAcceptAnyNotificationShapedMessage() {
        // the 202 is keyed on the absence of a JSON-RPC id, not on the method name
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.pingNotification());
        JsonRpcAssertions.assertNotificationAccepted(response);
    }

    // --- Capabilities ---

    @Test
    void shouldDeclareAllCapabilities() {
        McpHttpResponse response = transport().post("/mcp", McpTestRequests.initializeRequest(50), Map.of());

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

    // --- Negative session tests ---

    @Test
    void shouldRejectRequestWithInvalidSessionId() {
        McpHttpResponse response = postMcp("bogus-session-id-12345", McpTestRequests.toolsListRequest(70));
        JsonObject error = JsonRpcAssertions.assertHttpJsonRpcError(response, 404, 70, -32001);
        assertThat(error.getString("message")).contains("Invalid or missing Mcp-Session-Id");
    }

    @Test
    void shouldRejectForeignOrigin() {
        McpHttpResponse response = transport()
                .post("/mcp", McpTestRequests.initializeRequest(71), Map.of("Origin", "https://evil.example"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void shouldAcceptLocalhostOrigin() {
        McpHttpResponse response = transport()
                .post("/mcp", McpTestRequests.initializeRequest(72), Map.of("Origin", "http://localhost:3000"));

        JsonRpcAssertions.assertJsonRpcSuccess(response, 72);
    }

    // --- Modern protocol (2026-07-28) ---

    private McpHttpResponse postModern(Object id, String method, String name, String paramsJson, String caps) {
        return transport()
                .post(
                        "/mcp",
                        McpModernTestRequests.body(id, method, paramsJson, caps),
                        McpModernTestRequests.headers(method, name));
    }

    @Test
    void shouldDiscoverSupportedVersions() {
        JsonObject result =
                JsonRpcAssertions.assertJsonRpcSuccess(postModern(100, "server/discover", null, "", "{}"), 100);

        assertThat(result.getString("resultType")).isEqualTo("complete");
        assertThat(result.getJsonArray("supportedVersions").getValuesAs(JsonString::getString))
                .containsExactly(MODERN_VERSION, "2025-03-26");
        assertThat(result.getJsonObject("capabilities")).containsKey("tools");
    }

    @Test
    void shouldServeModernRequestsWithoutSession() {
        McpHttpResponse response = postModern(101, "tools/list", null, "", "{}");

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(response, 101);
        assertThat(response.header(MCP_SESSION_ID)).isNull();
        assertThat(result.getString("resultType")).isEqualTo("complete");
        assertThat(result.getJsonArray("tools").getValuesAs(JsonObject.class))
                .extracting(t -> t.getString("name"))
                .contains(GREET, ASK_NAME);
    }

    @Test
    void shouldCallToolWithModernRequest() {
        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(
                postModern(102, "tools/call", GREET, "\"name\":\"greet\",\"arguments\":{\"name\":\"Ada\"}", "{}"), 102);

        assertThat(result.getJsonArray(CONTENT).getJsonObject(0).getString("text"))
                .isEqualTo("Hello, Ada!");
    }

    @Test
    void shouldRejectMismatchedNameHeader() {
        McpHttpResponse response =
                postModern(103, "tools/call", "other", "\"name\":\"greet\",\"arguments\":{\"name\":\"Ada\"}", "{}");

        JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 103, -32020);
    }

    @Test
    void shouldRejectUnsupportedProtocolVersion() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":104,\"method\":\"tools/list\",\"params\":{"
                + McpModernTestRequests.meta("2099-01-01", "{}") + "}}";
        Map<String, String> headers = new HashMap<>(McpModernTestRequests.headers("tools/list", null));
        headers.put("MCP-Protocol-Version", "2099-01-01");

        JsonObject error =
                JsonRpcAssertions.assertHttpJsonRpcError(transport().post("/mcp", body, headers), 400, 104, -32022);

        assertThat(error.getJsonObject("data").getJsonArray("supported").getString(0))
                .isEqualTo(MODERN_VERSION);
    }

    @Test
    void shouldReturn404ForLegacyOnlyMethodInModernEra() {
        JsonRpcAssertions.assertHttpJsonRpcError(postModern(105, "ping", null, "", "{}"), 404, 105, -32601);
    }

    @Test
    void shouldAcceptModernNotificationWith202() {
        McpHttpResponse response = postModern(null, "notifications/cancelled", null, "\"requestId\":1", "{}");

        assertThat(response.statusCode()).isEqualTo(202);
    }

    @Test
    void shouldRoundTripElicitationWithMrtr() {
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
        assertThat(second.getJsonArray(CONTENT).getJsonObject(0).getString("text"))
                .isEqualTo("Hello, Ada!");
    }

    @Test
    void shouldRequireDeclaredElicitationCapability() {
        McpHttpResponse response =
                postModern(108, "tools/call", ASK_NAME, "\"name\":\"askName\",\"arguments\":{}", "{}");

        JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 108, -32021);
    }

    @Test
    void shouldAcknowledgeListenSubscription() throws Exception {
        String body = McpModernTestRequests.body(
                109, "subscriptions/listen", "\"notifications\":{\"toolsListChanged\":true}", "{}");
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create(transport().baseUrl() + "/mcp"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        McpModernTestRequests.headers("subscriptions/listen", null).forEach(request::header);
        request.setHeader("Accept", "text/event-stream");

        // The stream stays open after the acknowledgement: receiving it within the timeout proves the server delivers
        // events immediately instead of holding them in a response output buffer. SSE allows an optional space after
        // "data:" and runtimes differ on it.
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

    @Test
    void shouldRejectListenWithMismatchedProtocolVersionHeader() {
        String body = McpModernTestRequests.body(
                110, "subscriptions/listen", "\"notifications\":{\"toolsListChanged\":true}", "{}");
        Map<String, String> headers = new HashMap<>(McpModernTestRequests.headers("subscriptions/listen", null));
        headers.put("MCP-Protocol-Version", "2099-01-01");
        headers.put("Accept", "application/json, text/event-stream");

        JsonRpcAssertions.assertHttpJsonRpcError(transport().post("/mcp", body, headers), 400, 110, -32020);
    }

    // --- Helpers ---

    /**
     * Sends an {@code initialize} request and returns the session id.
     *
     * @return the MCP session id
     */
    protected String initializeSession() {
        McpHttpResponse response = transport().post("/mcp", McpTestRequests.initializeRequest(1), Map.of());
        JsonRpcAssertions.assertJsonRpcSuccess(response, 1);
        String sessionId = response.header(MCP_SESSION_ID);
        assertThat(sessionId).as(MCP_SESSION_ID + " header").isNotNull().isNotBlank();
        return sessionId;
    }

    /**
     * Posts a JSON-RPC body to the MCP endpoint using the given session.
     *
     * @param sessionId the MCP session id
     * @param body the JSON-RPC request body
     * @return the HTTP response
     */
    protected McpHttpResponse postMcp(String sessionId, String body) {
        return transport().post("/mcp", body, Map.of(MCP_SESSION_ID, sessionId));
    }

    /**
     * Builds a new {@link McpClient} connected to the test MCP endpoint, with automatic era detection.
     *
     * @return a new MCP client
     */
    protected McpClient buildClient() {
        return buildClient(null);
    }

    /**
     * Builds a new {@link McpClient} connected to the test MCP endpoint.
     *
     * @param protocolVersion forced protocol version, or {@code null} for automatic era detection
     * @return a new MCP client
     */
    protected McpClient buildClient(String protocolVersion) {
        DefaultMcpClient.Builder builder = DefaultMcpClient.builder()
                .transport(StreamableHttpMcpTransport.builder()
                        .url(transport().baseUrl() + "/mcp")
                        .build());
        if (protocolVersion != null) {
            builder.protocolVersion(protocolVersion);
        }
        return builder.build();
    }
}
