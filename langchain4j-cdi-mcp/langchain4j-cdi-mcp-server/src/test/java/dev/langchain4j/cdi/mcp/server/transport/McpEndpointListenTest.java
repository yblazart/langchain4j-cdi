package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.protocol.McpMetaKeys;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.HttpHeaders;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpEndpointListenTest {

    McpEndpoint endpoint;
    McpSubscriptionRegistry registry;
    McpContinuationStore store;
    final FakeSseEventSink sink = new FakeSseEventSink();
    final FakeSse sse = new FakeSse();

    @BeforeEach
    void setup() {
        McpServerConfigResolver resolver = new McpServerConfigResolver(new McpServerConfig());
        McpMrtrSupport support = new McpMrtrSupport(resolver);
        registry = new McpSubscriptionRegistry();
        store = new McpContinuationStore(support);
        endpoint = new McpEndpoint();
        endpoint.config = resolver;
        endpoint.modern =
                new McpModernProtocolHandler(mock(McpFeatureService.class), resolver, registry, support, store);
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
        store.shutdown();
    }

    static HttpHeaders headers(Map<String, String> values) {
        Map<String, String> lowerCase = new HashMap<>();
        values.forEach((k, v) -> lowerCase.put(k.toLowerCase(Locale.ROOT), v));
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getHeaderString(anyString()))
                .thenAnswer(
                        inv -> lowerCase.get(inv.getArgument(0, String.class).toLowerCase(Locale.ROOT)));
        return headers;
    }

    static Map<String, String> modernHeaders(String method, String version) {
        return Map.of("Mcp-Method", method, "MCP-Protocol-Version", version, "Accept", "text/event-stream");
    }

    static String modernBody(Object id, String method, JsonObjectBuilder params) {
        JsonObject meta = Json.createObjectBuilder()
                .add(McpMetaKeys.PROTOCOL_VERSION, "2026-07-28")
                .add(McpMetaKeys.CLIENT_CAPABILITIES, Json.createObjectBuilder())
                .build();
        return Json.createObjectBuilder()
                .add("jsonrpc", "2.0")
                .add("id", ((Number) id).longValue())
                .add("method", method)
                .add("params", params.add(McpMetaKeys.META, meta))
                .build()
                .toString();
    }

    static JsonObjectBuilder listenParams() {
        return Json.createObjectBuilder()
                .add("notifications", Json.createObjectBuilder().add("toolsListChanged", true));
    }

    @Test
    void rejectsProtocolVersionHeaderMismatchBeforeTouchingTheSink() {
        String body = modernBody(7, "subscriptions/listen", listenParams());

        assertThatThrownBy(() -> endpoint.handleListen(
                        body, headers(modernHeaders("subscriptions/listen", "2099-01-01")), sink, sse))
                .isInstanceOfSatisfying(McpException.class, e -> {
                    assertThat(e.getErrorCode().getCode()).isEqualTo(-32020);
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                });
        assertThat(sink.events()).isEmpty();
        assertThat(sink.isClosed()).isFalse();
    }

    @Test
    void rejectsOtherMethodsCalledDirectlyOnTheListenRoute() {
        String body = modernBody(8, "tools/list", Json.createObjectBuilder());

        assertThatThrownBy(() ->
                        endpoint.handleListen(body, headers(modernHeaders("tools/list", "2026-07-28")), sink, sse))
                .isInstanceOfSatisfying(McpException.class, e -> {
                    assertThat(e.getErrorCode().getCode()).isEqualTo(-32601);
                    assertThat(e.getHttpStatus()).isEqualTo(404);
                });
        assertThat(sink.events()).isEmpty();
    }

    @Test
    void rejectsLegacyRequestsCalledDirectlyOnTheListenRoute() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"subscriptions/listen\",\"params\":{}}";

        assertThatThrownBy(() -> endpoint.handleListen(body, headers(Map.of()), sink, sse))
                .isInstanceOfSatisfying(
                        McpException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
        assertThat(sink.events()).isEmpty();
    }

    @Test
    void returnsAfterAcknowledgingAndKeepsTheSinkOpenUntilServerShutdown() {
        String body = modernBody(10, "subscriptions/listen", listenParams());
        HttpHeaders headers = headers(modernHeaders("subscriptions/listen", "2026-07-28"));

        // the request thread must not be held for the lifetime of the stream: containers such as OpenLiberty wait for
        // running requests before stopping an application, before the registry could close the stream
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> endpoint.handleListen(body, headers, sink, sse));

        assertThat(sink.rendered())
                .startsWith("event: message\ndata: ")
                .contains("notifications/subscriptions/acknowledged");
        assertThat(registry.size()).isEqualTo(1);
        assertThat(sink.isClosed()).isFalse();

        registry.shutdown();

        assertThat(sink.rendered()).contains("\"resultType\":\"complete\"");
        assertThat(sink.isClosed()).isTrue();
    }
}
