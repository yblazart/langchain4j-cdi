package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.cdi.mcp.server.fixtures.HeaderArgTool;
import dev.langchain4j.cdi.mcp.server.fixtures.WeatherTool;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import dev.langchain4j.cdi.mcp.server.protocol.McpImplementation;
import dev.langchain4j.cdi.mcp.server.registry.McpToolDescriptor;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SEP-2243, request half: the {@code Mcp-Param-<designation>} headers a conforming client mirrors back on a
 * {@code tools/call} must be validated against the request body before the tool is dispatched.
 *
 * <p>The rules and the expectations come from the conformance scenario
 * {@code src/scenarios/server/http-standard-headers.ts} ({@code HttpCustomHeaderServerValidationScenario}) and from
 * {@code src/seps/sep-2243.yaml} in {@code modelcontextprotocol/conformance}.
 */
class McpParamHeaderValidationTest {

    /** {@code valid_designations}: tenant -> X-Tenant-Id (string), attempt -> X-Attempt (int), dryRun -> X-Dry-Run. */
    static final String TOOL = "valid_designations";

    McpFeatureService features;
    McpModernProtocolHandler handler;
    McpSubscriptionRegistry registry;
    McpContinuationStore store;

    @BeforeEach
    void setup() throws Exception {
        features = mock(McpFeatureService.class);
        when(features.serverInfo()).thenReturn(new McpImplementation("srv", "1.0"));
        when(features.callTool(any(), any(), any(), isNull()))
                .thenReturn(Json.createObjectBuilder()
                        .add("content", Json.createArrayBuilder())
                        .build());
        when(features.findTool(anyString())).thenReturn(Optional.empty());
        when(features.findTool(TOOL)).thenReturn(Optional.of(descriptor(HeaderArgTool.class, "valid")));

        registry = new McpSubscriptionRegistry();
        McpServerConfigResolver resolver = new McpServerConfigResolver(new McpServerConfig());
        McpMrtrSupport support = new McpMrtrSupport(resolver);
        store = new McpContinuationStore(support);
        handler = new McpModernProtocolHandler(features, resolver, registry, support, store);
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
        store.shutdown();
    }

    static McpToolDescriptor descriptor(Class<?> type, String methodName) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return McpToolDescriptor.fromMethod(type, m);
            }
        }
        throw new IllegalStateException("no method " + methodName + " on " + type);
    }

    static McpProtocolContext modern() {
        return new McpProtocolContext(McpEra.MODERN, "2026-07-28", JsonValue.EMPTY_JSON_OBJECT, null, null);
    }

    static JsonObject parse(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    /** Mirrors the container's case-insensitive {@code HttpHeaders#getHeaderString}. */
    static Function<String, String> headers(String... pairs) {
        Map<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map::get;
    }

    static JsonObject call(String tool, JsonObject arguments) {
        return Json.createObjectBuilder()
                .add("name", tool)
                .add("arguments", arguments)
                .build();
    }

    /** The three designated arguments of {@code valid_designations}, plus the undesignated one. */
    static JsonObjectBuilder args(String tenant) {
        return Json.createObjectBuilder()
                .add("tenant", tenant)
                .add("attempt", 3)
                .add("dryRun", false)
                .add("plain", "ignored");
    }

    static Function<String, String> designatedHeaders(String tenantHeader) {
        return headers(
                "Mcp-Param-X-Tenant-Id", tenantHeader, "Mcp-Param-X-Attempt", "3", "Mcp-Param-X-Dry-Run", "false");
    }

    McpReply post(JsonObject params, Function<String, String> headers) {
        return handler.handle(new JsonRpcRequest(1, "tools/call", params), modern(), false, headers);
    }

    static void assertRejected(McpReply reply) {
        assertThat(reply.status()).isEqualTo(400);
        assertThat(parse(reply.body()).getJsonObject("error").getInt("code")).isEqualTo(-32020);
    }

    static void assertAccepted(McpReply reply) {
        assertThat(reply.status()).isEqualTo(200);
        assertThat(parse(reply.body()).containsKey("error")).isFalse();
    }

    // --- Rule 6: both present and equal after decoding -> accept ---

    @Test
    void headerMatchingTheBodyIsAccepted() {
        assertAccepted(post(call(TOOL, args("acme").build()), designatedHeaders("acme")));
    }

    @Test
    void integerAndBooleanBodyValuesAreComparedInTheirEncodedForm() {
        // sep-2243-client-encode-values: integers as decimal strings, booleans as lowercase true/false
        assertAccepted(post(call(TOOL, args("acme").build()), designatedHeaders("acme")));
        assertRejected(post(
                call(TOOL, args("acme").build()),
                headers(
                        "Mcp-Param-X-Tenant-Id", "acme",
                        "Mcp-Param-X-Attempt", "4",
                        "Mcp-Param-X-Dry-Run", "false")));
        assertRejected(post(
                call(TOOL, args("acme").build()),
                headers(
                        "Mcp-Param-X-Tenant-Id", "acme",
                        "Mcp-Param-X-Attempt", "3",
                        "Mcp-Param-X-Dry-Run", "False")));
    }

    @Test
    void headerNamesAreMatchedCaseInsensitively() {
        // sep-2243-header-name-case-insensitive
        assertAccepted(post(
                call(TOOL, args("acme").build()),
                headers(
                        "mcp-param-x-tenant-id", "acme",
                        "MCP-PARAM-X-ATTEMPT", "3",
                        "Mcp-Param-x-dry-run", "false")));
    }

    // --- Rule 6: both present and unequal -> 400 / -32020 ---

    @Test
    void headerDisagreeingWithTheBodyIsRejected() {
        assertRejected(post(call(TOOL, args("acme").build()), designatedHeaders("evilcorp")));
    }

    @Test
    void headerValueComparisonIsCaseSensitive() {
        assertRejected(post(call(TOOL, args("acme").build()), designatedHeaders("ACME")));
    }

    // --- Rule 2: body value present, header absent -> 400 / -32020 ---

    @Test
    void headerOmittedWhileTheBodyCarriesAValueIsRejected() {
        // scenario: ServerRejectsMissingCustomHeader -> sep-2243-server-validate-param-match
        assertRejected(post(
                call(TOOL, args("acme").build()), headers("Mcp-Param-X-Attempt", "3", "Mcp-Param-X-Dry-Run", "false")));
    }

    // --- Rule 1: body value absent or null -> the header must be absent too ---

    @Test
    void bothAbsentIsAccepted() {
        JsonObject arguments = Json.createObjectBuilder()
                .add("attempt", 3)
                .add("dryRun", false)
                .add("plain", "ignored")
                .build();

        assertAccepted(
                post(call(TOOL, arguments), headers("Mcp-Param-X-Attempt", "3", "Mcp-Param-X-Dry-Run", "false")));
    }

    @Test
    void jsonNullBodyValueIsTreatedAsAbsent() {
        JsonObject arguments = Json.createObjectBuilder()
                .addNull("tenant")
                .add("attempt", 3)
                .add("dryRun", false)
                .build();

        assertAccepted(
                post(call(TOOL, arguments), headers("Mcp-Param-X-Attempt", "3", "Mcp-Param-X-Dry-Run", "false")));
        assertRejected(post(call(TOOL, arguments), designatedHeaders("acme")));
    }

    @Test
    void headerPresentWhileTheBodyValueIsAbsentIsRejected() {
        JsonObject arguments = Json.createObjectBuilder()
                .add("attempt", 3)
                .add("dryRun", false)
                .build();

        assertRejected(post(call(TOOL, arguments), designatedHeaders("acme")));
    }

    // --- Rules 4 and 5: the =?base64?...?= wrapper ---

    @Test
    void validBase64IsDecodedAndMatched() {
        // scenario: ServerAcceptsValidBase64 -> sep-2243-server-decode-base64
        String encoded =
                "=?base64?" + Base64.getEncoder().encodeToString("Hello".getBytes(StandardCharsets.UTF_8)) + "?=";

        assertAccepted(post(call(TOOL, args("Hello").build()), designatedHeaders(encoded)));
    }

    @Test
    void nonAsciiBodyValuesTravelBase64Wrapped() {
        String encoded =
                "=?base64?" + Base64.getEncoder().encodeToString("métêo".getBytes(StandardCharsets.UTF_8)) + "?=";

        assertAccepted(post(call(TOOL, args("métêo").build()), designatedHeaders(encoded)));
    }

    @Test
    void aValueWithoutTheWrapperIsComparedLiterally() {
        // scenario: ServerLiteralMissingBase64Prefix / ServerLiteralMissingBase64Suffix
        // -> sep-2243-server-validate-param-match
        assertAccepted(post(call(TOOL, args("SGVsbG8=").build()), designatedHeaders("SGVsbG8=")));
        assertAccepted(post(call(TOOL, args("=?base64?SGVsbG8=").build()), designatedHeaders("=?base64?SGVsbG8=")));
        // and a literal that happens to be valid Base64 is NOT decoded
        assertRejected(post(call(TOOL, args("Hello").build()), designatedHeaders("SGVsbG8=")));
    }

    /**
     * The reference implementation calls {@code Base64.getDecoder().decode(...)} unguarded, so a malformed payload
     * escapes as {@code IllegalArgumentException} and surfaces as HTTP 500. SEP-2243 asks for 400.
     */
    @Test
    void malformedBase64YieldsFourHundredNotFiveHundred() {
        // scenario: ServerRejectsInvalidBase64Padding / ServerRejectsInvalidBase64Chars
        // -> sep-2243-server-reject-invalid-param-chars
        for (String malformed :
                new String[] {"=?base64?SGVsbG8?=", "=?base64?SGVs!!!bG8=?=", "=?base64?SGVsbG8===?="}) {
            McpReply reply = post(call(TOOL, args("Hello").build()), designatedHeaders(malformed));

            assertThat(reply.status()).as(malformed).isEqualTo(400);
            assertThat(parse(reply.body()).getJsonObject("error").getInt("code"))
                    .as(malformed)
                    .isEqualTo(-32020);
        }
        verify(features, never()).callTool(any(), any(), any(), isNull());
    }

    // --- Rule 3: characters the spec forbids in a header value ---

    @Test
    void headerValueWithForbiddenCharactersIsRejected() {
        // sep-2243-server-reject-invalid-param-chars
        assertRejected(post(call(TOOL, args("ac\nme").build()), designatedHeaders("ac\nme")));
        assertRejected(post(call(TOOL, args("métêo").build()), designatedHeaders("métêo")));
    }

    // --- A tool without designations is wholly unaffected ---

    @Test
    void aToolWithoutDesignationsIsNeverValidated() {
        when(features.findTool("getWeather")).thenReturn(Optional.of(descriptor(WeatherTool.class, "getWeather")));
        JsonObject params = call(
                "getWeather", Json.createObjectBuilder().add("city", "Paris").build());

        // every Mcp-Param-* header is ignored, matching or not
        assertAccepted(post(params, headers("Mcp-Param-X-Tenant-Id", "whatever")));
        assertAccepted(post(params, headers()));
    }

    @Test
    void anUnknownToolIsLeftToTheFeatureService() {
        JsonObject params =
                call("no_such_tool", Json.createObjectBuilder().add("x", 1).build());

        assertAccepted(post(params, headers("Mcp-Param-X-Tenant-Id", "whatever")));
    }

    @Test
    void onlyToolsCallIsValidated() {
        when(features.getPrompt(any(), any(), any(), isNull()))
                .thenReturn(Json.createObjectBuilder()
                        .add("messages", Json.createArrayBuilder())
                        .build());
        JsonObject params = Json.createObjectBuilder()
                .add("name", TOOL)
                .add("arguments", args("acme").build())
                .build();

        McpReply reply = handler.handle(
                new JsonRpcRequest(1, "prompts/get", params), modern(), false, designatedHeaders("evilcorp"));

        assertAccepted(reply);
    }

    /** The three-argument overload has no headers to validate against, so it must not reject. */
    @Test
    void theHeaderlessOverloadSkipsValidation() {
        McpReply reply = handler.handle(
                new JsonRpcRequest(1, "tools/call", call(TOOL, args("acme").build())), modern(), false);

        assertAccepted(reply);
    }

    /** {@code decodeStrict} must not be reached with a non-primitive body value. */
    @Test
    void nonPrimitiveBodyValueIsTreatedAsAbsent() {
        JsonObject arguments = Json.createObjectBuilder()
                .add("tenant", Json.createObjectBuilder().add("nested", true))
                .add("attempt", 3)
                .add("dryRun", false)
                .build();

        assertRejected(post(call(TOOL, arguments), designatedHeaders("acme")));
    }

    @Test
    void descriptorPublishesItsDesignations() {
        assertThat(descriptor(HeaderArgTool.class, "valid").getHeaderDesignations())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("tenant", "X-Tenant-Id", "attempt", "X-Attempt", "dryRun", "X-Dry-Run"));
        assertThat(descriptor(WeatherTool.class, "getWeather").getHeaderDesignations())
                .isEmpty();
    }
}
