package dev.langchain4j.cdi.mcp.server.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Decodes MCP header values that may use the {@code =?base64?...?=} sentinel encoding. */
public final class McpHeaderValueCodec {

    static final String PREFIX = "=?base64?";
    static final String SUFFIX = "?=";

    private McpHeaderValueCodec() {}

    /**
     * Decodes a header value.
     *
     * @param headerValue raw header value, may be {@code null}
     * @return the decoded value, or {@code null}
     * @throws IllegalArgumentException if the Base64 payload is invalid
     */
    public static String decode(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        if (headerValue.length() >= PREFIX.length() + SUFFIX.length()
                && headerValue.startsWith(PREFIX)
                && headerValue.endsWith(SUFFIX)) {
            String payload = headerValue.substring(PREFIX.length(), headerValue.length() - SUFFIX.length());
            return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
        }
        return headerValue;
    }

    /**
     * Returns whether the value contains characters not allowed in an HTTP field value (RFC 9110).
     *
     * @param value header value
     * @return {@code true} if invalid characters are present
     */
    public static boolean hasInvalidCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\t' && (c < 0x20 || c > 0x7E)) {
                return true;
            }
        }
        return false;
    }
}
