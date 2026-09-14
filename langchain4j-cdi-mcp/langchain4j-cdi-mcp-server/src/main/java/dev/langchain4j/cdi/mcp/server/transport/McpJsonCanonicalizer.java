package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/** Order-independent JSON representation used to bind a requestState to the request arguments. */
public final class McpJsonCanonicalizer {

    private McpJsonCanonicalizer() {}

    public static String canonicalize(JsonValue value) {
        StringBuilder out = new StringBuilder();
        write(value == null ? JsonValue.NULL : value, out);
        return out.toString();
    }

    public static String digest(JsonValue value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalize(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void write(JsonValue value, StringBuilder out) {
        if (value instanceof JsonObject object) {
            out.append('{');
            boolean first = true;
            for (String key : new TreeSet<>(object.keySet())) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(Json.createValue(key)).append(':');
                write(object.get(key), out);
            }
            out.append('}');
        } else if (value instanceof JsonArray array) {
            out.append('[');
            for (int i = 0; i < array.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(array.get(i), out);
            }
            out.append(']');
        } else {
            out.append(value);
        }
    }
}
