package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@ApplicationScoped
public class McpToolInvoker {

    @Inject
    BeanManager beanManager;

    public Object invoke(McpToolDescriptor descriptor, JsonObject arguments) {
        Object instance = resolveCdiBean(descriptor.getBeanType());
        Object[] args = resolveArguments(descriptor.getMethod(), arguments);

        try {
            return descriptor.getMethod().invoke(instance, args);
        } catch (InvocationTargetException e) {
            throw new McpException(
                    null,
                    McpErrorCode.INTERNAL_ERROR,
                    "Tool invocation failed: " + descriptor.getName() + " - "
                            + e.getCause().getMessage());
        } catch (IllegalAccessException e) {
            throw new McpException(
                    null, McpErrorCode.INTERNAL_ERROR, "Tool invocation failed: " + descriptor.getName());
        }
    }

    @SuppressWarnings("unchecked")
    private Object resolveCdiBean(Class<?> beanType) {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(beanType));
        if (bean == null) {
            throw new McpException(null, McpErrorCode.INTERNAL_ERROR, "CDI bean not found for: " + beanType.getName());
        }
        CreationalContext<?> ctx = beanManager.createCreationalContext(bean);
        return beanManager.getReference(bean, beanType, ctx);
    }

    private Object[] resolveArguments(Method method, JsonObject arguments) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            String paramName = params[i].getName();
            if (arguments != null && arguments.containsKey(paramName)) {
                args[i] = convertJsonValue(arguments.get(paramName), params[i].getType());
            } else {
                args[i] = getDefaultValue(params[i].getType());
            }
        }
        return args;
    }

    private Object convertJsonValue(JsonValue jsonValue, Class<?> targetType) {
        if (jsonValue == null || jsonValue.getValueType() == JsonValue.ValueType.NULL) {
            return getDefaultValue(targetType);
        }
        if (targetType == String.class) {
            if (jsonValue instanceof JsonString) {
                return ((JsonString) jsonValue).getString();
            }
            return jsonValue.toString();
        }
        if (targetType == int.class || targetType == Integer.class) {
            return ((JsonNumber) jsonValue).intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return ((JsonNumber) jsonValue).longValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return ((JsonNumber) jsonValue).doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return (float) ((JsonNumber) jsonValue).doubleValue();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return jsonValue.getValueType() == JsonValue.ValueType.TRUE;
        }
        return jsonValue.toString();
    }

    private Object getDefaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == boolean.class) return false;
        return null;
    }
}
