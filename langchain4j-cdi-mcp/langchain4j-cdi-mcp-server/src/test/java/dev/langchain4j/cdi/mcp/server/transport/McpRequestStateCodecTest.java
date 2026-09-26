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
import java.util.List;
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
    void pendingKeysRoundTrip() {
        String token = codec.encode(new McpRequestStateCodec.State(
                "tools/call",
                "askName",
                "d1",
                codec.expiresAt(Duration.ofMinutes(10)),
                null,
                null,
                List.of("input-1")));

        McpRequestStateCodec.State state = codec.decode(1, token, "tools/call", "askName", "d1");

        assertThat(state.pendingKeys()).containsExactly("input-1");
        assertThat(state.responses()).isEmpty();
    }

    @Test
    void stateWithoutPendingKeysDecodesWithNoPendingKeys() {
        McpRequestStateCodec.State state = codec.decode(1, token(), "tools/call", "askName", "d1");

        assertThat(state.pendingKeys()).isEmpty();
    }

    static byte[] hmac(byte[] payload) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(SECRET, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aV2TokenWithAnAlteredNonceCharacterIsRejected() {
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
    void theTokenIsVersionTwoAndHidesThePayload() {
        String token = token();

        assertThat(token).startsWith("v2.");
        String decoded =
                new String(java.util.Base64.getUrlDecoder().decode(token.substring(3)), StandardCharsets.ISO_8859_1);
        assertThat(decoded).doesNotContain("input-0", "accept", "askName", "tools/call");
    }

    @Test
    void twoTokensOfTheSameStateDiffer() {
        assertThat(token()).isNotEqualTo(token());
    }

    @Test
    void anAlteredCiphertextIsRejected() {
        String token = token();
        byte[] blob = java.util.Base64.getUrlDecoder().decode(token.substring(3));
        for (int i : new int[] {0, 12, blob.length - 1}) {
            byte[] altered = blob.clone();
            altered[i] ^= 0x01;
            String tampered =
                    "v2." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(altered);

            assertInvalid(() -> codec.decode(1, tampered, "tools/call", "askName", "d1"));
        }
    }

    @Test
    void aTruncatedTokenIsRejected() {
        String token = token();

        assertInvalid(() -> codec.decode(1, token.substring(0, 20), "tools/call", "askName", "d1"));
        assertInvalid(() -> codec.decode(1, "v2.", "tools/call", "askName", "d1"));
    }

    /** A first-format token, signed with {@link #SECRET}, of the given payload. */
    static String versionOneToken(JsonObject payload) {
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "."
                + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(bytes));
    }

    static JsonObject versionOnePayload(long expiresAt) {
        return Json.createObjectBuilder()
                .add("v", 1)
                .add("m", "tools/call")
                .add("n", "askName")
                .add("d", "d1")
                .add("e", expiresAt)
                .build();
    }

    @Test
    void aV2TokenThatIsNotBase64OrHoldsOnlyATagIsRejected() {
        String tagOnly =
                "v2." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[28]);

        assertInvalid(() -> codec.decode(1, "v2.!!!", "tools/call", "askName", "d1"));
        assertInvalid(() -> codec.decode(1, tagOnly, "tools/call", "askName", "d1"));
    }

    /**
     * The first format, signed but not encrypted, is no longer read (#304): no released version ever issued it, only
     * snapshots between the MRTR work and #303.
     */
    @Test
    void aValidFirstFormatTokenIsRejected() {
        String token = versionOneToken(versionOnePayload(NOW.toEpochMilli() + 60_000));

        assertInvalid(() -> codec.decode(1, token, "tools/call", "askName", "d1"));
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
