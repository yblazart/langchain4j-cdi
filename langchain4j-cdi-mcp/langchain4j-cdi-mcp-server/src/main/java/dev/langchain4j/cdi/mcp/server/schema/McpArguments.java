package dev.langchain4j.cdi.mcp.server.schema;

import dev.langchain4j.cdi.mcp.server.api.McpFrameworkTypes;
import dev.langchain4j.cdi.mcp.server.error.McpArgumentDefinitionException;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.tools.ToolArg;

/**
 * The per-parameter rules of the mcp-java {@code @ToolArg}/{@code @PromptArg} contract that the advertised schema and
 * the argument binder must agree on: whether an argument is required, what its default value is, and how a textual
 * value (a {@code defaultValue}, a prompt argument, a resource-template variable) converts to the parameter type.
 */
public final class McpArguments {

    private McpArguments() {}

    /**
     * Returns whether the client must supply the argument bound to this parameter. A parameter is not required when its
     * type is {@link Optional}, {@link OptionalInt}, {@link OptionalLong} or {@link OptionalDouble}, or when its
     * {@code @ToolArg}/{@code @PromptArg} sets {@code required = false} or a non-empty {@code defaultValue}; any other
     * parameter, with or without an annotation, is required.
     *
     * @param param the method parameter, not an MCP framework type
     * @return {@code true} if the argument is required
     */
    public static boolean isRequired(Parameter param) {
        if (isOptionalType(param.getType())) {
            return false;
        }
        ToolArg toolArg = param.getAnnotation(ToolArg.class);
        if (toolArg != null) {
            return toolArg.required() && toolArg.defaultValue().isEmpty();
        }
        PromptArg promptArg = param.getAnnotation(PromptArg.class);
        if (promptArg != null) {
            return promptArg.required() && promptArg.defaultValue().isEmpty();
        }
        return true;
    }

    /**
     * Returns the non-empty {@code defaultValue} of the parameter's {@code @ToolArg} or {@code @PromptArg}.
     *
     * @param param the method parameter
     * @return the default value as written in the annotation, or {@code null} when the parameter declares none
     */
    public static String defaultValue(Parameter param) {
        ToolArg toolArg = param.getAnnotation(ToolArg.class);
        if (toolArg != null && !toolArg.defaultValue().isEmpty()) {
            return toolArg.defaultValue();
        }
        PromptArg promptArg = param.getAnnotation(PromptArg.class);
        if (promptArg != null && !promptArg.defaultValue().isEmpty()) {
            return promptArg.defaultValue();
        }
        return null;
    }

    /**
     * Returns whether a parameter type is one of the containers the mcp-java contract makes optional.
     *
     * @param type the parameter type
     * @return {@code true} for {@link Optional}, {@link OptionalInt}, {@link OptionalLong} and {@link OptionalDouble}
     */
    public static boolean isOptionalType(Class<?> type) {
        return type == Optional.class
                || type == OptionalInt.class
                || type == OptionalLong.class
                || type == OptionalDouble.class;
    }

    /**
     * Returns the type of the value a parameter carries: {@code T} for {@code Optional<T>} (its raw type when {@code T}
     * is itself parameterized, {@code Object} when it cannot be resolved), {@code int}/{@code long}/{@code double} for
     * {@code OptionalInt}/{@code OptionalLong}/{@code OptionalDouble}, and the parameter type otherwise.
     *
     * @param param the method parameter
     * @return the type the schema describes and the binder converts to
     */
    public static Class<?> valueType(Parameter param) {
        Class<?> type = param.getType();
        if (type == OptionalInt.class) {
            return int.class;
        }
        if (type == OptionalLong.class) {
            return long.class;
        }
        if (type == OptionalDouble.class) {
            return double.class;
        }
        if (type == Optional.class && param.getParameterizedType() instanceof ParameterizedType optional) {
            Type argument = optional.getActualTypeArguments()[0];
            if (argument instanceof Class<?> c) {
                return c;
            }
            if (argument instanceof ParameterizedType p && p.getRawType() instanceof Class<?> raw) {
                return raw;
            }
            return Object.class;
        }
        return type == Optional.class ? Object.class : type;
    }

    /**
     * Returns the JSON Schema {@code type} advertised for a parameter type.
     *
     * @param type the parameter type
     * @return {@code string}, {@code integer}, {@code number}, {@code boolean}, {@code array} or {@code object}
     */
    public static String jsonType(Class<?> type) {
        return JsonSchemaGenerator.mapJavaTypeToJsonSchema(type);
    }

    /**
     * Returns the values advertised for an enum parameter: the {@link Enum#name()} of each constant, in declaration
     * order. The binder accepts exactly these values.
     *
     * @param enumType the enum type
     * @return the constant names
     */
    public static List<String> enumNames(Class<?> enumType) {
        List<String> names = new ArrayList<>();
        for (Object constant : enumType.getEnumConstants()) {
            names.add(((Enum<?>) constant).name());
        }
        return names;
    }

    /**
     * Returns whether a value of this type can be written as text: {@code String}, the primitive types and their
     * wrappers, and enums. Only such parameters accept a {@code defaultValue}.
     *
     * @param type the parameter type
     * @return {@code true} if {@link #parse(Class, String)} supports the type
     */
    public static boolean hasTextualForm(Class<?> type) {
        return type == String.class
                || (type.isPrimitive() && type != void.class)
                || type == Integer.class
                || type == Long.class
                || type == Short.class
                || type == Byte.class
                || type == Double.class
                || type == Float.class
                || type == Boolean.class
                || type == Character.class
                || type.isEnum();
    }

    /**
     * Converts a textual value to a parameter type. Numbers use the {@code valueOf} of their wrapper, a boolean must be
     * {@code true} or {@code false} (case-insensitive), a {@code char} must be a single character, and an enum constant
     * is matched on its {@link Enum#name()}.
     *
     * @param type the parameter type, one that {@link #hasTextualForm(Class)} accepts
     * @param text the textual value
     * @return the converted value
     * @throws IllegalArgumentException if the text does not denote a value of that type, or the type has no textual
     *     form
     */
    public static Object parse(Class<?> type, String text) {
        if (type == String.class) {
            return text;
        }
        if (type == int.class || type == Integer.class) {
            return Integer.valueOf(text);
        }
        if (type == long.class || type == Long.class) {
            return Long.valueOf(text);
        }
        if (type == short.class || type == Short.class) {
            return Short.valueOf(text);
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.valueOf(text);
        }
        if (type == double.class || type == Double.class) {
            return Double.valueOf(text);
        }
        if (type == float.class || type == Float.class) {
            return Float.valueOf(text);
        }
        if (type == boolean.class || type == Boolean.class) {
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("not a boolean: " + text);
        }
        if (type == char.class || type == Character.class) {
            if (text.length() == 1) {
                return text.charAt(0);
            }
            throw new IllegalArgumentException("not a single character: " + text);
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(text)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("not one of " + enumNames(type) + ": " + text);
        }
        throw new IllegalArgumentException("no textual form for " + type.getName());
    }

    /**
     * Converts a bound value to the JSON value that stands for it in a schema, for the {@code default} keyword.
     *
     * @param value a value returned by {@link #parse(Class, String)}
     * @return the JSON value: a number, a boolean, or a string (an enum constant is written as its name)
     */
    public static JsonValue toJson(Object value) {
        if (value instanceof Boolean b) {
            return b ? JsonValue.TRUE : JsonValue.FALSE;
        }
        if (value instanceof Float f) {
            // through its decimal text: widening 0.1f to double would advertise 0.10000000149011612
            return Json.createValue(new BigDecimal(f.toString()));
        }
        if (value instanceof Double d) {
            return Json.createValue(d);
        }
        if (value instanceof Number n) {
            return Json.createValue(n.longValue());
        }
        if (value instanceof Enum<?> e) {
            return Json.createValue(e.name());
        }
        return Json.createValue(String.valueOf(value));
    }

    /**
     * Checks, at registration, that every {@code defaultValue} of the method's parameters converts to its parameter
     * type.
     *
     * @param owner the feature being registered, for the failure message, for example {@code Tool 'list_tasks'}
     * @param method the {@code @Tool} or {@code @Prompt} method
     * @throws McpArgumentDefinitionException if a default does not convert, or annotates a parameter whose type has no
     *     textual form
     */
    public static void validateDefaults(String owner, Method method) {
        for (Parameter param : method.getParameters()) {
            if (McpFrameworkTypes.isFrameworkType(param.getType())) {
                continue;
            }
            String defaultValue = defaultValue(param);
            if (defaultValue == null) {
                continue;
            }
            Class<?> type = valueType(param);
            String prefix = owner + ", parameter '" + McpParameterNames.resolve(param) + "': defaultValue \""
                    + defaultValue + "\" ";
            if (!hasTextualForm(type)) {
                throw new McpArgumentDefinitionException(prefix
                        + "is not supported on a parameter of type "
                        + type.getSimpleName()
                        + "; defaults apply to String, primitive, wrapper and enum parameters only");
            }
            try {
                parse(type, defaultValue);
            } catch (IllegalArgumentException e) {
                throw new McpArgumentDefinitionException(prefix + "cannot be converted to "
                        + type.getSimpleName()
                        + (type.isEnum() ? " " + enumNames(type) : ""));
            }
        }
    }
}
