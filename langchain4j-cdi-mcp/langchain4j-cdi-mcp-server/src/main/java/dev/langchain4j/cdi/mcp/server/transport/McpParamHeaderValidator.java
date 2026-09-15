package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpProtocolErrors;
import dev.langchain4j.cdi.mcp.server.protocol.McpHeaderValueCodec;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.Map;
import java.util.function.Function;

/**
 * Validates the {@code Mcp-Param-<designation>} headers a conforming client mirrors back on a {@code tools/call},
 * against the arguments carried in the request body (SEP-2243, request half).
 *
 * <p>Every rule below is taken from {@code src/seps/sep-2243.yaml} and from the conformance scenario
 * {@code src/scenarios/server/http-standard-headers.ts} ({@code HttpCustomHeaderServerValidationScenario}) in
 * {@code modelcontextprotocol/conformance}:
 *
 * <ol>
 *   <li><b>Header name.</b> The header is {@code Mcp-Param-} followed by the <em>designation</em> — the
 *       {@code x-mcp-header} value — not by the argument name. The scenario sends {@code [`Mcp-Param-${headerSuffix}`]:
 *       headerValue} with {@code headerSuffix = paramDef['x-mcp-header']}. Header <em>names</em> are matched
 *       case-insensitively ({@code sep-2243-header-name-case-insensitive}), which the container's header lookup already
 *       provides.
 *   <li><b>Value encoding.</b> {@code sep-2243-client-encode-values}: an integer is mirrored as its decimal string, a
 *       boolean as lowercase {@code true}/{@code false}; a string travels as itself. The comparison of the decoded
 *       value is exact and case-sensitive.
 *   <li><b>Base64 sentinel.</b> {@code sep-2243-client-base64-unsafe} / {@code sep-2243-server-decode-base64}: a value
 *       that cannot be carried as plain ASCII arrives as {@code =?base64?{payload}?=} and MUST be decoded before
 *       comparison. A value missing either delimiter is a literal, asserted by the scenario's
 *       {@code ServerLiteralMissingBase64Prefix} and {@code ServerLiteralMissingBase64Suffix} cases.
 *   <li><b>Malformed payload.</b> {@code sep-2243-server-reject-invalid-param-chars}, and the scenario's
 *       {@code ServerRejectsInvalidBase64Padding} / {@code ServerRejectsInvalidBase64Chars} cases: a payload with
 *       invalid padding or non-alphabet characters is rejected — never decoded leniently, and never allowed to escape
 *       as an unchecked exception.
 *   <li><b>Omitted values.</b> {@code sep-2243-client-omit-null} / {@code sep-2243-server-not-expect-null}: a
 *       {@code null} or absent argument means the client omits the header, so the server must not require it. The
 *       converse — a body value with no header — is a mismatch, asserted by the scenario's
 *       {@code ServerRejectsMissingCustomHeader} case against {@code sep-2243-server-validate-param-match}.
 *   <li><b>Rejection shape.</b> {@code sep-2243-server-reject-param-mismatch}: HTTP 400 with JSON-RPC error code
 *       {@code -32020}, which {@link McpProtocolErrors#headerMismatch} already produces.
 * </ol>
 *
 * <p>A tool that designates no argument is never inspected, and headers whose designation this tool does not declare
 * are ignored — SEP-2243 asks intermediaries to forward an unrecognized {@code Mcp-Param-*} header and otherwise leave
 * it alone, and a server has no more claim on it.
 */
public final class McpParamHeaderValidator {

    /** The prefix of the header a client mirrors a designated argument into. */
    public static final String HEADER_PREFIX = "Mcp-Param-";

    private McpParamHeaderValidator() {}

    /**
     * Validates every designated argument of one {@code tools/call} request.
     *
     * @param id the JSON-RPC request id, carried on the error
     * @param designations the tool's designations, argument name to {@code x-mcp-header} value
     * @param arguments the {@code arguments} object of the {@code tools/call} parameters
     * @param headers the request's header lookup, case-insensitive on the name
     * @throws McpException with {@code -32020} and HTTP 400 if any designated argument fails validation
     */
    public static void validate(
            Object id, Map<String, String> designations, JsonObject arguments, Function<String, String> headers) {
        for (Map.Entry<String, String> designation : designations.entrySet()) {
            String header = HEADER_PREFIX + designation.getValue();
            String raw = headers.apply(header);
            String expected = mirroredValue(arguments.get(designation.getKey()));

            if (raw == null) {
                if (expected != null) {
                    // rule 5: the body carries a value the client did not mirror
                    throw McpProtocolErrors.headerMismatch(
                            id, header + " is missing but the body carries '" + designation.getKey() + "'");
                }
                continue;
            }
            if (expected == null) {
                // rule 5, converse: the header claims a value the body does not carry
                throw McpProtocolErrors.headerMismatch(
                        id, header + " is present but the body carries no '" + designation.getKey() + "' value");
            }
            if (McpHeaderValueCodec.hasInvalidCharacters(raw)) {
                throw McpProtocolErrors.headerMismatch(id, header + " contains characters invalid in a header value");
            }
            String value;
            try {
                value = McpHeaderValueCodec.decodeStrict(raw);
            } catch (IllegalArgumentException e) {
                // rule 4: a malformed payload is a 400, never an unchecked exception surfacing as a 500
                throw McpProtocolErrors.headerMismatch(
                        id, header + " carries a malformed Base64 payload: " + e.getMessage());
            }
            if (!expected.equals(value)) {
                throw McpProtocolErrors.headerMismatch(
                        id, header + " does not match the body value of '" + designation.getKey() + "'");
            }
        }
    }

    /**
     * Returns the string a conforming client mirrors into the header for a body value, or {@code null} when there is
     * nothing to mirror.
     *
     * <p>{@code null}, an absent argument and a non-primitive value all return {@code null}: the first two because
     * {@code sep-2243-client-omit-null} tells the client to omit the header, the third because a designation may only
     * be applied to an {@code integer}, {@code string} or {@code boolean} argument
     * ({@code sep-2243-x-mcp-header-primitive-only}, enforced at registration by
     * {@code dev.langchain4j.cdi.mcp.server.schema.McpHeaderValidator}), so an object or array here is a body that does
     * not match its own schema and cannot be mirrored either way.
     *
     * @param value the argument value read from the request body, may be {@code null}
     * @return the value as the client would encode it in the header, or {@code null}
     */
    static String mirroredValue(JsonValue value) {
        if (value == null) {
            return null;
        }
        return switch (value.getValueType()) {
            case STRING -> ((JsonString) value).getString();
            // sep-2243-client-encode-values: integers as their decimal string representation
            case NUMBER ->
                ((JsonNumber) value).isIntegral()
                        ? ((JsonNumber) value).bigIntegerValue().toString()
                        : value.toString();
            case TRUE -> "true";
            case FALSE -> "false";
            default -> null;
        };
    }
}
