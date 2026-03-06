package dev.langchain4j.cdi.mcp.server.schema;

import dev.langchain4j.cdi.mcp.server.McpToolArg;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Set;

public class JsonSchemaGenerator {

    private JsonSchemaGenerator() {}

    public static JsonObject fromMethod(Method method) {
        JsonObjectBuilder schema = Json.createObjectBuilder().add("type", "object");
        JsonObjectBuilder properties = Json.createObjectBuilder();
        JsonArrayBuilder required = Json.createArrayBuilder();

        for (Parameter param : method.getParameters()) {
            String paramName = resolveParamName(param);
            String description = param.isAnnotationPresent(McpToolArg.class)
                    ? param.getAnnotation(McpToolArg.class).value()
                    : "";

            properties.add(paramName, buildPropertySchema(param.getType(), description));
            required.add(paramName);
        }

        return schema.add("properties", properties).add("required", required).build();
    }

    private static String resolveParamName(Parameter param) {
        if (param.isAnnotationPresent(McpToolArg.class)) {
            // @McpToolArg value is the description, the param name comes from reflection
        }
        return param.getName();
    }

    private static JsonObject buildPropertySchema(Class<?> type, String description) {
        JsonObjectBuilder prop = Json.createObjectBuilder();
        prop.add("type", mapJavaTypeToJsonSchema(type));
        if (!description.isEmpty()) {
            prop.add("description", description);
        }
        if (type.isEnum()) {
            JsonArrayBuilder enumValues = Json.createArrayBuilder();
            for (Object constant : type.getEnumConstants()) {
                enumValues.add(constant.toString());
            }
            prop.add("enum", enumValues);
        }
        return prop.build();
    }

    private static String mapJavaTypeToJsonSchema(Class<?> type) {
        if (type == String.class || type == char.class || type == Character.class) {
            return "string";
        }
        if (type == int.class
                || type == Integer.class
                || type == long.class
                || type == Long.class
                || type == short.class
                || type == Short.class
                || type == byte.class
                || type == Byte.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type) || type.isArray()) {
            return "array";
        }
        if (type.isEnum()) {
            return "string";
        }
        return "object";
    }
}
