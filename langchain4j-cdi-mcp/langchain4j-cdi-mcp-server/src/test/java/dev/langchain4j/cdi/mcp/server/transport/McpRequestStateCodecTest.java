package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.error.McpException;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class McpRequestStateCodecTest {

    static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");

    final McpRequestStateCodec codec = new McpRequestStateCodec(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    String token() {
        JsonObject responses = Json.createObjectBuilder()
                .add("input-0", Json.createObjectBuilder().add("action", "accept"))
                .build();
        return codec.encode(new McpRequestStateCodec.State(
                "tools/call", "askName", "d1", codec.expiresAt(Duration.ofMinutes(10)), responses, null));
    }

    @Test
    void roundTrip() {
        McpRequestStateCodec.State state = codec.decode(1, token(), "tools/call", "askName", "d1");

        assertThat(state.responses().getJsonObject("input-0").getString("action"))
                .isEqualTo("accept");
        assertThat(state.continuationId()).isNull();
        assertThat(state.expiresAtMillis()).isEqualTo(NOW.toEpochMilli() + 600_000);
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = token();
        int dot = token.indexOf('.');
        char c = token.charAt(dot + 1);
        String tampered = token.substring(0, dot + 1) + (c == 'A' ? 'B' : 'A') + token.substring(dot + 2);

        assertInvalid(() -> codec.decode(1, tampered, "tools/call", "askName", "d1"));
    }

    @Test
    void stateFromAnotherSecretIsRejected() {
        McpRequestStateCodec other = new McpRequestStateCodec(
                "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8), Clock.fixed(NOW, ZoneOffset.UTC));

        assertInvalid(() -> other.decode(1, token(), "tools/call", "askName", "d1"));
    }

    @Test
    void stateBoundToOriginatingRequest() {
        assertInvalid(() -> codec.decode(1, token(), "prompts/get", "askName", "d1"));
        assertInvalid(() -> codec.decode(1, token(), "tools/call", "other", "d1"));
        assertInvalid(() -> codec.decode(1, token(), "tools/call", "askName", "d2"));
    }

    @Test
    void expiredStateIsRejected() {
        McpRequestStateCodec later =
                new McpRequestStateCodec(SECRET, Clock.fixed(NOW.plus(Duration.ofMinutes(11)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.decode(1, token(), "tools/call", "askName", "d1"))
                .isInstanceOfSatisfying(
                        McpException.class, e -> assertThat(e.getMessage()).contains("Expired"));
    }

    @Test
    void garbageIsRejected() {
        assertInvalid(() -> codec.decode(1, "not-a-token", "tools/call", "askName", "d1"));
        assertInvalid(() -> codec.decode(1, "!!!.???", "tools/call", "askName", "d1"));
    }

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> new McpRequestStateCodec(new byte[16], Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(McpException.class, e -> {
            assertThat(e.getErrorCode().getCode()).isEqualTo(-32602);
            assertThat(e.getHttpStatus()).isEqualTo(400);
        });
    }
}
