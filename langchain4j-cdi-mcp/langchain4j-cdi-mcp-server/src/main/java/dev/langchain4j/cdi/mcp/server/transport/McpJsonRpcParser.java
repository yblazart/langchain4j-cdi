package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcRequest;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.StringReader;

/** Parses raw JSON-RPC bodies received on the MCP endpoint. */
public final class McpJsonRpcParser {

    private McpJsonRpcParser() {}

    /**
     * Returns whether the given body is a JSON-RPC response (as opposed to a request or notification).
     *
     * @param body the raw request body
     * @return {@code true} if the body looks like a client response
     */
    public static boolean isJsonRpcResponse(String body) {
        try {
            return isJsonRpcResponse(parseJson(body));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns whether the given parsed JSON object is a JSON-RPC response.
     *
     * @param json the parsed JSON object
     * @return {@code true} if the object looks like a client response
     */
    public static boolean isJsonRpcResponse(JsonObject json) {
        return !json.containsKey("method") && (json.containsKey("result") || json.containsKey("error"));
    }

    /**
     * Parses a raw JSON body into a {@link JsonObject}.
     *
     * @param body the raw request body
     * @return the parsed JSON object
     * @throws McpException with error code {@link McpErrorCode#PARSE_ERROR} if the body is not valid JSON
     */
    public static JsonObject parseJson(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        } catch (JsonException | ClassCastException e) {
            throw new McpException(null, McpErrorCode.PARSE_ERROR, "Parse error: invalid JSON-RPC message", 400, null);
        }
    }

    /**
     * Parses a raw JSON-RPC request body.
     *
     * @param body the raw request body
     * @return the parsed request
     * @throws McpException with error code {@link McpErrorCode#PARSE_ERROR} if the body is not valid JSON-RPC
     */
    public static JsonRpcRequest parseRequest(String body) {
        return parseRequest(parseJson(body));
    }

    /**
     * Builds a {@link JsonRpcRequest} from a pre-parsed JSON object.
     *
     * @param json the parsed JSON object
     * @return the parsed request
     */
    public static JsonRpcRequest parseRequest(JsonObject json) {
        JsonObject params = json.get("params") instanceof JsonObject p ? p : null;
        JsonRpcRequest request = new JsonRpcRequest(extractId(json), json.getString("method", null), params);
        if (params != null && params.get("_meta") instanceof JsonObject meta && meta.containsKey("progressToken")) {
            request.setProgressToken(jsonPrimitive(meta.get("progressToken")));
        }
        return request;
    }

    /**
     * Extracts the {@code id} field from a JSON-RPC object.
     *
     * @param json the JSON-RPC object
     * @return the id as a primitive value, or {@code null} if absent or JSON {@code null}
     */
    public static Object extractId(JsonObject json) {
        return json.containsKey("id") ? jsonPrimitive(json.get("id")) : null;
    }

    /**
     * Converts a JSON value into its corresponding Java primitive: {@link String} for strings, {@link Long} for
     * numbers, {@code null} for JSON {@code null}, and the JSON text for anything else.
     *
     * @param value the JSON value, may be {@code null}
     * @return the extracted primitive, or {@code null}
     */
    public static Object jsonPrimitive(JsonValue value) {
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            return null;
        }
        if (value instanceof JsonString s) {
            return s.getString();
        }
        if (value instanceof JsonNumber n) {
            return n.longValue();
        }
        return value.toString();
    }
}
