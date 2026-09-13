package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpEraDetectorTest {

    private static JsonObjectBuilder modernMeta(String version) {
        return Json.createObjectBuilder()
                .add("io.modelcontextprotocol/protocolVersion", version)
                .add(
                        "io.modelcontextprotocol/clientCapabilities",
                        Json.createObjectBuilder().add("elicitation", JsonValue.EMPTY_JSON_OBJECT))
                .add(
                        "io.modelcontextprotocol/clientInfo",
                        Json.createObjectBuilder().add("name", "c").add("version", "1"));
    }

    private static JsonRpcRequest toolCall(JsonObjectBuilder meta) {
        JsonObjectBuilder params =
                Json.createObjectBuilder().add("name", "greet").add("arguments", JsonValue.EMPTY_JSON_OBJECT);
        if (meta != null) {
            params.add("_meta", meta);
        }
        return new JsonRpcRequest(1, "tools/call", params.build());
    }

    private static Map<String, String> modernHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("MCP-Protocol-Version", "2026-07-28");
        headers.put("Mcp-Method", "tools/call");
        headers.put("Mcp-Name", "greet");
        return headers;
    }

    @Test
    void requestWithoutMetaOrHeaderIsLegacy2025_03_26() {
        McpProtocolContext ctx = McpEraDetector.detect(toolCall(null), h -> null);

        assertThat(ctx.era()).isEqualTo(McpEra.LEGACY);
        assertThat(ctx.protocolVersion()).isEqualTo("2025-03-26");
    }

    @Test
    void legacyHeaderVersionIsKept() {
        McpProtocolContext ctx =
                McpEraDetector.detect(toolCall(null), Map.of("MCP-Protocol-Version", "2025-11-25")::get);

        assertThat(ctx.isModern()).isFalse();
        assertThat(ctx.protocolVersion()).isEqualTo("2025-11-25");
    }

    @Test
    void modernRequestIsDetected() {
        JsonObjectBuilder meta = modernMeta("2026-07-28").add("io.modelcontextprotocol/logLevel", "info");

        McpProtocolContext ctx = McpEraDetector.detect(toolCall(meta), modernHeaders()::get);

        assertThat(ctx.isModern()).isTrue();
        assertThat(ctx.hasClientCapability("elicitation")).isTrue();
        assertThat(ctx.clientInfo().getString("name")).isEqualTo("c");
        assertThat(ctx.logLevel()).isEqualTo(McpLogLevel.info);
    }

    @Test
    void modernHeaderWithoutMetaIsHeaderMismatch() {
        assertThatThrownBy(() -> McpEraDetector.detect(toolCall(null), modernHeaders()::get))
                .isInstanceOfSatisfying(
                        McpException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(-32020));
    }

    @Test
    void unknownVersionIsUnsupported() {
        Map<String, String> headers = modernHeaders();
        headers.put("MCP-Protocol-Version", "2099-01-01");

        assertThatThrownBy(() -> McpEraDetector.detect(toolCall(modernMeta("2099-01-01")), headers::get))
                .isInstanceOfSatisfying(McpException.class, e -> {
                    assertThat(e.getErrorCode().getCode()).isEqualTo(-32022);
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                });
    }

    @Test
    void versionHeaderMismatchIsRejected() {
        Map<String, String> headers = modernHeaders();
        headers.remove("MCP-Protocol-Version");

        assertThatThrownBy(() -> McpEraDetector.detect(toolCall(modernMeta("2026-07-28")), headers::get))
                .isInstanceOfSatisfying(
                        McpException.class, e -> assertThat(e.getMessage()).contains("MCP-Protocol-Version"));
    }

    @Test
    void methodHeaderMismatchIsRejected() {
        Map<String, String> headers = modernHeaders();
        headers.put("Mcp-Method", "tools/list");

        assertThatThrownBy(() -> McpEraDetector.detect(toolCall(modernMeta("2026-07-28")), headers::get))
                .isInstanceOfSatisfying(
                        McpException.class, e -> assertThat(e.getMessage()).contains("Mcp-Method"));
    }

    @Test
    void missingNameHeaderIsRejected() {
        Map<String, String> headers = modernHeaders();
        headers.remove("Mcp-Name");

        assertThatThrownBy(() -> McpEraDetector.detect(toolCall(modernMeta("2026-07-28")), headers::get))
                .isInstanceOfSatisfying(
                        McpException.class, e -> assertThat(e.getMessage()).contains("Mcp-Name"));
    }

    @Test
    void base64NameHeaderIsDecodedBeforeComparison() {
        Map<String, String> headers = modernHeaders();
        headers.put("Mcp-Name", "=?base64?Z3JlZXQ=?=");

        assertThat(McpEraDetector.detect(toolCall(modernMeta("2026-07-28")), headers::get)
                        .isModern())
                .isTrue();
    }

    @Test
    void resourcesReadComparesUri() {
        JsonObject params = Json.createObjectBuilder()
                .add("uri", "config://app")
                .add("_meta", modernMeta("2026-07-28"))
                .build();
        Map<String, String> headers = modernHeaders();
        headers.put("Mcp-Method", "resources/read");
        headers.put("Mcp-Name", "config://app");

        assertThat(McpEraDetector.detect(new JsonRpcRequest(1, "resources/read", params), headers::get)
                        .isModern())
                .isTrue();
    }

    @Test
    void missingClientCapabilitiesIsInvalidParams() {
        JsonObjectBuilder meta =
                Json.createObjectBuilder().add("io.modelcontextprotocol/protocolVersion", "2026-07-28");

        assertThatThrownBy(() -> McpEraDetector.detect(toolCall(meta), modernHeaders()::get))
                .isInstanceOfSatisfying(McpException.class, e -> {
                    assertThat(e.getErrorCode().getCode()).isEqualTo(-32602);
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                });
    }

    @Test
    void unknownLogLevelIsInvalidParams() {
        JsonObjectBuilder meta = modernMeta("2026-07-28").add("io.modelcontextprotocol/logLevel", "verbose");

        assertThatThrownBy(() -> McpEraDetector.detect(toolCall(meta), modernHeaders()::get))
                .isInstanceOfSatisfying(
                        McpException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(-32602));
    }
}
