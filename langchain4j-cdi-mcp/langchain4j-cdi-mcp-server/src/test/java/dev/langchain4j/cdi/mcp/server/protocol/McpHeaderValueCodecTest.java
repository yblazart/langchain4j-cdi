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
}
