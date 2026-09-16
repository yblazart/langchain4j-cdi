package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpProtocolErrors;
import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import dev.langchain4j.cdi.mcp.server.protocol.McpHeaderValueCodec;
import dev.langchain4j.cdi.mcp.server.protocol.McpHttpHeaders;
import dev.langchain4j.cdi.mcp.server.protocol.McpMetaKeys;
import dev.langchain4j.cdi.mcp.server.protocol.McpProtocolVersions;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.Map;
import java.util.function.Function;

/** Determines the protocol era of a request and validates modern request metadata headers. */
public final class McpEraDetector {

    private static final Map<String, String> NAME_SOURCE_FIELDS =
            Map.of("tools/call", "name", "prompts/get", "name", "resources/read", "uri");

    private McpEraDetector() {}

    /**
     * Detects the era of a request.
     *
     * @param request parsed JSON-RPC request
     * @param headers header lookup (case-insensitive lookup is the caller's responsibility)
     * @return the protocol context
     */
    public static McpProtocolContext detect(JsonRpcRequest request, Function<String, String> headers) {
        Object id = request.getId();
        JsonObject meta = meta(request.getParams());
        String bodyVersion = string(meta, McpMetaKeys.PROTOCOL_VERSION);
        String headerVersion = headers.apply(McpHttpHeaders.PROTOCOL_VERSION);

        if (bodyVersion == null) {
            if (McpProtocolVersions.isModernEra(headerVersion)) {
                // SEP-2575: a missing _meta, or a _meta missing a required subfield, is malformed input, not a
                // header/_meta disagreement - it must be answered -32602 Invalid params, not -32020 HeaderMismatch.
                throw McpProtocolErrors.invalidParams(
                        id,
                        "Missing _meta." + McpMetaKeys.PROTOCOL_VERSION + " required by "
                                + McpHttpHeaders.PROTOCOL_VERSION + ": " + headerVersion);
            }
            return McpProtocolContext.legacy(
                    headerVersion != null ? headerVersion : McpProtocolVersions.LEGACY_2025_03_26);
        }
        // the header/_meta mismatch check runs first: a version the server does not support, announced consistently
        // in both places, is an UnsupportedProtocolVersion error, but a disagreement between the two is a
        // HeaderMismatch whatever the versions are.
        if (!bodyVersion.equals(headerVersion)) {
            throw McpProtocolErrors.headerMismatch(id, McpHttpHeaders.PROTOCOL_VERSION);
        }
        if (!McpProtocolVersions.isSupportedModern(bodyVersion)) {
            throw McpProtocolErrors.unsupportedProtocolVersion(id, bodyVersion);
        }
        requireHeaderEquals(id, headers, McpHttpHeaders.METHOD, request.getMethod(), false);

        String nameField = NAME_SOURCE_FIELDS.get(request.getMethod());
        String bodyName = nameField != null ? string(request.getParams(), nameField) : null;
        if (bodyName != null) {
            requireHeaderEquals(id, headers, McpHttpHeaders.NAME, bodyName, true);
        }

        if (!(meta.get(McpMetaKeys.CLIENT_CAPABILITIES) instanceof JsonObject capabilities)) {
            throw McpProtocolErrors.invalidParams(id, "Missing _meta." + McpMetaKeys.CLIENT_CAPABILITIES);
        }
        JsonObject clientInfo = meta.get(McpMetaKeys.CLIENT_INFO) instanceof JsonObject o ? o : null;
        return new McpProtocolContext(McpEra.MODERN, bodyVersion, capabilities, clientInfo, parseLogLevel(id, meta));
    }

    private static void requireHeaderEquals(
            Object id, Function<String, String> headers, String header, String expected, boolean decode) {
        String raw = headers.apply(header);
        if (raw == null || McpHeaderValueCodec.hasInvalidCharacters(raw)) {
            throw McpProtocolErrors.headerMismatch(id, header);
        }
        String value;
        try {
            value = decode ? McpHeaderValueCodec.decode(raw) : raw;
        } catch (IllegalArgumentException e) {
            throw McpProtocolErrors.headerMismatch(id, header);
        }
        if (!expected.equals(value)) {
            throw McpProtocolErrors.headerMismatch(id, header);
        }
    }

    private static McpLogLevel parseLogLevel(Object id, JsonObject meta) {
        String level = string(meta, McpMetaKeys.LOG_LEVEL);
        if (level == null) {
            return null;
        }
        try {
            return McpLogLevel.valueOf(level);
        } catch (IllegalArgumentException e) {
            throw McpProtocolErrors.invalidParams(id, "Invalid log level: " + level);
        }
    }

    private static JsonObject meta(JsonObject params) {
        if (params != null && params.get(McpMetaKeys.META) instanceof JsonObject meta) {
            return meta;
        }
        return JsonValue.EMPTY_JSON_OBJECT;
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.get(key) instanceof JsonString s ? s.getString() : null;
    }
}
