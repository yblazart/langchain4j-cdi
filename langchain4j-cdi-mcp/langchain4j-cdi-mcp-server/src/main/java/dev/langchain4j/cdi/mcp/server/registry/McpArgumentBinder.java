package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.error.McpInvalidArgumentException;
import dev.langchain4j.cdi.mcp.server.schema.McpArguments;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.lang.reflect.Parameter;

/**
 * Converts one client-supplied JSON argument into the Java value of its method parameter.
 *
 * <p>A tool argument must have the JSON type the tool's {@code inputSchema} advertises: an integer for {@code int}/
 * {@code long}/{@code short}/{@code byte} and their wrappers (a fractional or out-of-range number is rejected, never
 * truncated), a number for {@code double}/{@code float}, {@code true}/{@code false} for a boolean, and for an enum a
 * string equal to one of the advertised constant names. A {@code String} parameter keeps its historical leniency: a
 * non-string value binds as its JSON text. Prompt arguments and resource-template variables are strings on the wire
 * ({@code GetPromptRequest.arguments} is {@code {[key: string]: string}}), so for those a string is parsed into the
 * parameter type as well.
 *
 * <p>An absent or {@code null} argument takes the parameter's {@code defaultValue}, else {@code null}, or
 * zero/{@code false} for a primitive. A value that cannot be bound raises {@link McpInvalidArgumentException}, whose
 * message names the argument and the expected type and never a Java class.
 */
final class McpArgumentBinder {

    /** Longest client-supplied string echoed back in an error message. */
    private static final int MAX_ECHOED_LENGTH = 40;

    private McpArgumentBinder() {}

    /**
     * Binds one argument.
     *
     * @param requestId the JSON-RPC request id, carried by a rejection
     * @param param the method parameter, not an MCP framework type
     * @param name the argument's wire name
     * @param value the JSON value sent by the client, or {@code null} when the argument is absent
     * @param textual {@code true} for a prompt argument or a resource-template variable, whose values are strings on
     *     the wire and are parsed into the parameter type
     * @return the Java argument
     * @throws McpInvalidArgumentException if the value cannot be bound to the parameter type
     */
    static Object bind(Object requestId, Parameter param, String name, JsonValue value, boolean textual) {
        Class<?> type = param.getType();
        if (value == null || value.getValueType() == JsonValue.ValueType.NULL) {
            String defaultValue = McpArguments.defaultValue(param);
            return defaultValue != null ? McpArguments.parse(type, defaultValue) : absentValue(type);
        }
        if (textual && type != String.class && value instanceof JsonString text && McpArguments.hasTextualForm(type)) {
            try {
                return McpArguments.parse(type, text.getString());
            } catch (IllegalArgumentException e) {
                throw invalid(requestId, name, type, value);
            }
        }
        return fromJson(requestId, name, type, value);
    }

    /**
     * Returns the value passed for an absent argument that has no default.
     *
     * @param type the parameter type
     * @return zero or {@code false} for a primitive, {@code null} otherwise
     */
    static Object absentValue(Class<?> type) {
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private static Object fromJson(Object requestId, String name, Class<?> type, JsonValue value) {
        if (type == String.class) {
            // lenient on purpose and unchanged: a number or a boolean sent for a string binds as its JSON text
            return value instanceof JsonString text ? text.getString() : value.toString();
        }
        if (type == boolean.class || type == Boolean.class) {
            if (value.getValueType() == JsonValue.ValueType.TRUE) {
                return Boolean.TRUE;
            }
            if (value.getValueType() == JsonValue.ValueType.FALSE) {
                return Boolean.FALSE;
            }
            throw invalid(requestId, name, type, value);
        }
        if (type.isEnum() || type == char.class || type == Character.class) {
            if (value instanceof JsonString text) {
                try {
                    return McpArguments.parse(type, text.getString());
                } catch (IllegalArgumentException e) {
                    throw invalid(requestId, name, type, value);
                }
            }
            throw invalid(requestId, name, type, value);
        }
        if (isNumeric(type)) {
            if (!(value instanceof JsonNumber number)) {
                throw invalid(requestId, name, type, value);
            }
            try {
                return toNumber(type, number);
            } catch (ArithmeticException e) {
                throw invalid(requestId, name, type, value);
            }
        }
        // arrays, collections and other objects are not converted yet: unchanged behaviour
        return value.toString();
    }

    private static boolean isNumeric(Class<?> type) {
        return type == int.class
                || type == Integer.class
                || type == long.class
                || type == Long.class
                || type == short.class
                || type == Short.class
                || type == byte.class
                || type == Byte.class
                || type == double.class
                || type == Double.class
                || type == float.class
                || type == Float.class;
    }

    private static Object toNumber(Class<?> type, JsonNumber number) {
        if (type == int.class || type == Integer.class) {
            return number.intValueExact();
        }
        if (type == long.class || type == Long.class) {
            return number.longValueExact();
        }
        if (type == short.class || type == Short.class) {
            return number.bigDecimalValue().shortValueExact();
        }
        if (type == byte.class || type == Byte.class) {
            return number.bigDecimalValue().byteValueExact();
        }
        if (type == float.class || type == Float.class) {
            return (float) number.doubleValue();
        }
        return number.doubleValue();
    }

    private static McpInvalidArgumentException invalid(Object requestId, String name, Class<?> type, JsonValue value) {
        return new McpInvalidArgumentException(
                requestId, name, "expected " + expected(type) + ", got " + describe(value));
    }

    private static String expected(Class<?> type) {
        if (type.isEnum()) {
            return "one of " + McpArguments.enumNames(type);
        }
        if (type == char.class || type == Character.class) {
            return "a single-character string";
        }
        return McpArguments.jsonType(type);
    }

    private static String describe(JsonValue value) {
        return switch (value.getValueType()) {
            case STRING -> "string \"" + truncate(((JsonString) value).getString()) + "\"";
            case NUMBER -> "number " + truncate(value.toString());
            case TRUE, FALSE -> "boolean " + value;
            case OBJECT -> "object";
            case ARRAY -> "array";
            case NULL -> "null";
        };
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ECHOED_LENGTH ? text : text.substring(0, MAX_ECHOED_LENGTH) + "...";
    }
}
