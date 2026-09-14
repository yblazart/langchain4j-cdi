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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class McpModernProtocolHandlerTest {

    McpFeatureService features;
    McpModernProtocolHandler handler;
    McpSubscriptionRegistry registry;

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
        handler = new McpModernProtocolHandler(features, new McpServerConfigResolver(new McpServerConfig()), registry);
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
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
        when(features.listTools(null))
                .thenReturn(Json.createObjectBuilder()
                        .add("tools", Json.createArrayBuilder())
                        .build());

        McpReply reply = handler.handle(new JsonRpcRequest(2, "tools/list", null), modern(null), true);

        assertThat(reply.isStream()).isFalse();
        assertThat(parse(reply.body()).getJsonObject("result").getString("resultType"))
                .isEqualTo("complete");
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
    void listenStreamsAcknowledgementUntilServerShutdown() throws Exception {
        JsonObject params = Json.createObjectBuilder()
                .add("notifications", Json.createObjectBuilder().add("toolsListChanged", true))
                .build();
        McpReply reply = handler.handle(new JsonRpcRequest(9, "subscriptions/listen", params), modern(null), true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Thread writer = new Thread(() -> {
            try {
                reply.stream().write(out);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        writer.start();
        long deadline = System.currentTimeMillis() + 5_000;
        while (registry.size() == 0 && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        registry.shutdown();
        writer.join(5_000);

        String sse = out.toString(StandardCharsets.UTF_8);
        assertThat(reply.isStream()).isTrue();
        assertThat(sse).contains("notifications/subscriptions/acknowledged").contains("\"resultType\":\"complete\"");
        assertThat(writer.isAlive()).isFalse();
    }
}
