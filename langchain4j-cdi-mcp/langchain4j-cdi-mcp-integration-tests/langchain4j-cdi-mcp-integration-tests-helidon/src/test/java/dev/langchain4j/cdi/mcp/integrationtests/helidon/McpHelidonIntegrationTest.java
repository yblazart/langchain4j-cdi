package dev.langchain4j.cdi.mcp.integrationtests.helidon;

import static dev.langchain4j.cdi.mcp.integrationtests.McpTestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.cdi.mcp.integrationtests.JsonRpcAssertions;
import dev.langchain4j.cdi.mcp.integrationtests.McpHttpResponse;
import dev.langchain4j.cdi.mcp.integrationtests.McpHttpTransport;
import dev.langchain4j.cdi.mcp.integrationtests.McpModernTestRequests;
import dev.langchain4j.cdi.mcp.integrationtests.McpTestRequests;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.ws.rs.client.WebTarget;
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

/**
 * Helidon integration tests. Unlike Quarkus, Helidon's {@code @HelidonTest} does not support test method inheritance,
 * so this class uses the shared helpers directly instead of extending {@code AbstractMcpIntegrationTest}.
 */
@SuppressWarnings("java:S5786")
@HelidonTest
public class McpHelidonIntegrationTest {

    @Inject
    WebTarget injectedTarget;

    private McpHttpTransport transport() {
        return new HelidonWebTargetTransport(injectedTarget);
    }

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
            assertThat(tools2).hasSizeGreaterThanOrEqualTo(2);
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
    void shouldRejectInitializeSentAsNotification() {
        // an id-less `initialize` must not orphan a session: no Mcp-Session-Id header, and a proper JSON-RPC error
        McpHttpResponse response = transport().post("/mcp", McpTestRequests.initializeNotification(), Map.of());

        assertThat(response.header(MCP_SESSION_ID))
                .as("no session should be created for an id-less initialize")
                .isNull();
        JsonRpcAssertions.assertJsonRpcError(
                response, null, -32600, "initialize must be a request, not a notification");

        // the (nonexistent) session cannot be reached: any follow-up request needs a session id (unlike `ping`,
        // which does not require one), and none was issued
        McpHttpResponse followUp = transport().post("/mcp", McpTestRequests.toolsListRequest(2), Map.of());
        assertThat(followUp.statusCode()).as("no usable session should remain").isEqualTo(400);
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
        JsonArray content = result.getJsonArray("content");
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
        assertThat(firstMessage).containsKey("content");
        assertThat(firstMessage.getJsonObject("content").getString("text")).contains("Hello world");
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
    void shouldAcceptRootsListChangedNotification() {
        String sessionId = initializeSession();
        McpHttpResponse response = postMcp(sessionId, McpTestRequests.rootsListChangedNotification());
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

        assertThat(result.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("Hello, Ada!");
    }

    @Test
    void shouldRejectMismatchedNameHeader() {
        McpHttpResponse response =
                postModern(103, "tools/call", "other", "\"name\":\"greet\",\"arguments\":{\"name\":\"Ada\"}", "{}");

        JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 103, -32020);
    }

    // --- SEP-2243 request half: Mcp-Param-<designation> validated against the body ---

    private McpHttpResponse postDesignatedCall(int id, String... paramHeaders) {
        return transport()
                .post(
                        "/mcp",
                        McpModernTestRequests.designatedCallBody(id),
                        McpModernTestRequests.designatedCallHeaders(paramHeaders));
    }

    @Test
    void shouldAcceptMirroredParamHeadersMatchingTheBody() {
        // the header names are deliberately mis-cased: SEP-2243 requires a case-insensitive name comparison, and the
        // lookup is the container's
        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(
                postDesignatedCall(130, "mcp-param-tenant-id", "acme", "MCP-PARAM-ATTEMPT", "7"), 130);

        assertThat(result.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("tenant=acme, attempt=7");
    }

    @Test
    void shouldRejectAParamHeaderDisagreeingWithTheBody() {
        McpHttpResponse response = postDesignatedCall(131, "Mcp-Param-Tenant-Id", "evilcorp", "Mcp-Param-Attempt", "7");

        JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 131, -32020);
    }

    @Test
    void shouldRejectAnOmittedParamHeaderWhenTheBodyCarriesTheValue() {
        McpHttpResponse response = postDesignatedCall(132, "Mcp-Param-Attempt", "7");

        JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 132, -32020);
    }

    /** A malformed Base64 payload must be a 400, not an unchecked exception surfacing as a 500. */
    @Test
    void shouldRejectMalformedBase64ParamHeaderWith400NotWith500() {
        for (String malformed : McpModernTestRequests.malformedBase64Values()) {
            McpHttpResponse response =
                    postDesignatedCall(133, "Mcp-Param-Tenant-Id", malformed, "Mcp-Param-Attempt", "7");

            assertThat(response.statusCode()).as(malformed).isEqualTo(400);
            JsonRpcAssertions.assertHttpJsonRpcError(response, 400, 133, -32020);
        }
    }

    /** The 2025-03-26 era predates SEP-2243: it must ignore every {@code Mcp-Param-*} header. */
    @Test
    void shouldIgnoreParamHeadersInTheLegacyEra() {
        String sessionId = initializeSession();
        Map<String, String> headers = new HashMap<>();
        headers.put(MCP_SESSION_ID, sessionId);
        // a value the modern era would reject twice over: it disagrees with the body, and its Base64 is malformed
        headers.put("Mcp-Param-Tenant-Id", "=?base64?ZXZpbA?=");

        JsonObject result = JsonRpcAssertions.assertJsonRpcSuccess(
                transport()
                        .post(
                                "/mcp",
                                McpTestRequests.toolsCallRequest(
                                        134, "tenantEcho", "{\"tenant\":\"acme\",\"attempt\":7}"),
                                headers),
                134);

        assertThat(result.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("tenant=acme, attempt=7");
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
        assertThat(second.getJsonArray("content").getJsonObject(0).getString("text"))
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

    private String initializeSession() {
        McpHttpResponse response = transport().post("/mcp", McpTestRequests.initializeRequest(1), Map.of());
        JsonRpcAssertions.assertJsonRpcSuccess(response, 1);
        String sessionId = response.header(MCP_SESSION_ID);
        assertThat(sessionId).as(MCP_SESSION_ID + " header").isNotNull().isNotBlank();
        return sessionId;
    }

    private McpHttpResponse postMcp(String sessionId, String body) {
        return transport().post("/mcp", body, Map.of(MCP_SESSION_ID, sessionId));
    }

    private McpClient buildClient() {
        return buildClient(null);
    }

    private McpClient buildClient(String protocolVersion) {
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
