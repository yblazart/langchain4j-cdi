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
        if (!isBase64Wrapped(headerValue)) {
            return headerValue;
        }
        String payload = headerValue.substring(PREFIX.length(), headerValue.length() - SUFFIX.length());
        return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
    }

    /**
     * Returns whether a header value carries the {@code =?base64?…?=} sentinel. Both delimiters are required: SEP-2243
     * says a value that is missing either one is a literal, not an encoded payload, and the conformance scenario
     * {@code http-custom-header-server-validation} asserts exactly that for a missing prefix and a missing suffix. The
     * length test keeps the prefix's trailing {@code ?} from doubling as the suffix's leading one, so
     * {@code =?base64?=} is a literal too.
     *
     * @param headerValue raw header value, may be {@code null}
     * @return {@code true} when the value is wrapped and its payload must be Base64-decoded
     */
    public static boolean isBase64Wrapped(String headerValue) {
        return headerValue != null
                && headerValue.length() >= PREFIX.length() + SUFFIX.length()
                && headerValue.startsWith(PREFIX)
                && headerValue.endsWith(SUFFIX);
    }

    /**
     * Decodes a header value, rejecting a malformed Base64 payload rather than repairing it.
     *
     * <p>{@link #decode} delegates to {@link Base64.Decoder#decode(String)}, which accepts an unpadded payload:
     * {@code =?base64?SGVsbG8?=} decodes to {@code Hello} there. SEP-2243's conformance test-case table requires a
     * server to <em>reject</em> a payload with invalid padding as well as one with non-alphabet characters, so this
     * method validates the payload's alphabet, length and padding before decoding it.
     *
     * @param headerValue raw header value, may be {@code null}
     * @return the decoded value, the value itself when it carries no {@code =?base64?…?=} sentinel, or {@code null}
     * @throws IllegalArgumentException if the sentinel is present but its payload is not strictly valid Base64
     */
    public static String decodeStrict(String headerValue) {
        if (!isBase64Wrapped(headerValue)) {
            return headerValue;
        }
        String payload = headerValue.substring(PREFIX.length(), headerValue.length() - SUFFIX.length());
        requireStrictBase64(payload);
        return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
    }

    /**
     * Rejects a payload that is not canonical Base64: its length must be a multiple of four, it may carry at most two
     * {@code =} and only as its last characters, and every other character must be in the standard alphabet.
     *
     * @param payload the payload found inside the {@code =?base64?…?=} sentinel
     * @throws IllegalArgumentException if the payload is not strictly valid Base64
     */
    private static void requireStrictBase64(String payload) {
        if (payload.length() % 4 != 0) {
            throw new IllegalArgumentException("Base64 payload length is not a multiple of 4");
        }
        int padding = 0;
        while (padding < payload.length() && payload.charAt(payload.length() - 1 - padding) == '=') {
            padding++;
        }
        if (padding > 2) {
            throw new IllegalArgumentException("Excessive Base64 padding");
        }
        for (int i = 0; i < payload.length() - padding; i++) {
            if (!isBase64Alphabet(payload.charAt(i))) {
                throw new IllegalArgumentException("Illegal Base64 character: " + payload.charAt(i));
            }
        }
    }

    private static boolean isBase64Alphabet(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '/';
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
