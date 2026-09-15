package dev.langchain4j.cdi.mcp.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class McpHeaderValueCodecTest {

    @Test
    void plainValueIsReturnedAsIs() {
        assertThat(McpHeaderValueCodec.decode("get_weather")).isEqualTo("get_weather");
    }

    @Test
    void base64SentinelIsDecoded() {
        String encoded =
                "=?base64?" + Base64.getEncoder().encodeToString("météo".getBytes(StandardCharsets.UTF_8)) + "?=";

        assertThat(McpHeaderValueCodec.decode(encoded)).isEqualTo("météo");
    }

    @Test
    void invalidBase64IsRejected() {
        assertThatThrownBy(() -> McpHeaderValueCodec.decode("=?base64?***?="))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullStaysNull() {
        assertThat(McpHeaderValueCodec.decode(null)).isNull();
    }

    @Test
    void controlCharactersAreInvalid() {
        assertThat(McpHeaderValueCodec.hasInvalidCharacters("a\nb")).isTrue();
        assertThat(McpHeaderValueCodec.hasInvalidCharacters("tools/call")).isFalse();
    }

    // --- SEP-2243 strict decoding of Mcp-Param-* values ---

    @Test
    void wrappedValueNeedsBothDelimiters() {
        assertThat(McpHeaderValueCodec.isBase64Wrapped("=?base64?SGVsbG8=?=")).isTrue();
        assertThat(McpHeaderValueCodec.isBase64Wrapped("SGVsbG8=")).isFalse();
        assertThat(McpHeaderValueCodec.isBase64Wrapped("=?base64?SGVsbG8=")).isFalse();
        // the prefix's trailing '?' and the suffix must not be the same character
        assertThat(McpHeaderValueCodec.isBase64Wrapped("=?base64?=")).isFalse();
        assertThat(McpHeaderValueCodec.isBase64Wrapped(null)).isFalse();
    }

    @Test
    void strictDecodeDecodesACorrectlyPaddedPayload() {
        assertThat(McpHeaderValueCodec.decodeStrict("=?base64?SGVsbG8=?=")).isEqualTo("Hello");
        String utf8 = "=?base64?" + Base64.getEncoder().encodeToString("météo".getBytes(StandardCharsets.UTF_8)) + "?=";
        assertThat(McpHeaderValueCodec.decodeStrict(utf8)).isEqualTo("météo");
    }

    @Test
    void strictDecodeTreatsAnUnwrappedValueAsALiteral() {
        assertThat(McpHeaderValueCodec.decodeStrict("SGVsbG8=")).isEqualTo("SGVsbG8=");
        assertThat(McpHeaderValueCodec.decodeStrict("=?base64?SGVsbG8=")).isEqualTo("=?base64?SGVsbG8=");
        assertThat(McpHeaderValueCodec.decodeStrict(null)).isNull();
    }

    /**
     * {@code Base64.getDecoder()} happily decodes an unpadded payload, so the lenient
     * {@link McpHeaderValueCodec#decode} accepts {@code SGVsbG8} and returns {@code Hello}. SEP-2243's conformance
     * test-case table requires strict rejection, so {@code decodeStrict} must check the padding itself.
     */
    @Test
    void strictDecodeRejectsAPayloadWithMissingPadding() {
        assertThat(McpHeaderValueCodec.decode("=?base64?SGVsbG8?=")).isEqualTo("Hello");

        assertThatThrownBy(() -> McpHeaderValueCodec.decodeStrict("=?base64?SGVsbG8?="))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void strictDecodeRejectsNonAlphabetCharacters() {
        assertThatThrownBy(() -> McpHeaderValueCodec.decodeStrict("=?base64?SGVs!!!bG8=?="))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void strictDecodeRejectsMisplacedOrExcessivePadding() {
        assertThatThrownBy(() -> McpHeaderValueCodec.decodeStrict("=?base64?SGVsbG8===?="))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpHeaderValueCodec.decodeStrict("=?base64?SG=sbG8=?="))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
