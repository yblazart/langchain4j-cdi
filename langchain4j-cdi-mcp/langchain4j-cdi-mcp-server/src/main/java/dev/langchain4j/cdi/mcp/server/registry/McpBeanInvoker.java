package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.api.McpApiFactory;
import dev.langchain4j.cdi.mcp.server.api.McpFrameworkTypes;
import dev.langchain4j.cdi.mcp.server.api.McpRequestContext;
import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.transport.McpInputRequiredSignal;
import dev.langchain4j.cdi.mcp.server.transport.McpSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Instance;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.resources.ResourceTemplateArg;
import org.mcpjava.server.tools.ToolArg;

/** Shared utility for invoking CDI bean methods with JSON arguments. */
@ApplicationScoped
public class McpBeanInvoker {

    /** CDI-required default constructor. */
    public McpBeanInvoker() {}

    private static final String DEFAULT_NAME = "<<element name>>";

    @Inject
    BeanManager beanManager;

    @Inject
    McpApiFactory apiFactory;

    /**
     * Container-specific {@link McpMethodInvoker} providers, consulted before reflection. Empty (never {@code null}) on
     * runtimes that do not ship an implementing module, e.g. Jakarta EE 10 / CDI 4.0.1 servers such as WildFly and
     * OpenLiberty.
     */
    @Inject
    Instance<McpInvokerProvider> invokerProviders;

    private final ConcurrentHashMap<Method, Optional<McpMethodInvoker>> invokerCache = new ConcurrentHashMap<>();

    /**
     * Invokes a method without MCP framework context (backward compatible).
     *
     * @param requestId the JSON-RPC request ID for error reporting
     * @param beanType the CDI bean class to look up and invoke
     * @param method the method to invoke on the resolved bean
     * @param arguments the JSON object containing method arguments
     * @return the method invocation result
     */
    public Object invoke(Object requestId, Class<?> beanType, Method method, JsonObject arguments) {
        return invoke(requestId, beanType, method, arguments, null, null);
    }

    /**
     * Invokes a method with MCP framework context, enabling framework type injection.
     *
     * @param requestId the JSON-RPC request ID for error reporting
     * @param beanType the CDI bean class to look up and invoke
     * @param method the method to invoke on the resolved bean
     * @param arguments the JSON object containing method arguments
     * @param ctx the MCP request context, or {@code null} if unavailable
     * @param session the MCP session, or {@code null} if unavailable
     * @return the method invocation result
     */
    public Object invoke(
            Object requestId,
            Class<?> beanType,
            Method method,
            JsonObject arguments,
            McpRequestContext ctx,
            McpSession session) {
        Optional<McpMethodInvoker> providedInvoker =
                invokerCache.computeIfAbsent(method, m -> lookupInvoker(beanType, m));
        if (providedInvoker.isPresent()) {
            return invokeViaProvider(requestId, beanType, method, arguments, ctx, session, providedInvoker.get());
        }
        return invokeViaReflection(requestId, beanType, method, arguments, ctx, session);
    }

    /**
     * Consults every registered {@link McpInvokerProvider}, in the order supplied by CDI, and returns the first invoker
     * offered for the given method. Never returns {@code null}.
     *
     * @param beanType the CDI bean class declaring the method
     * @param method the method to find an invoker for
     * @return the first matching invoker, or {@link Optional#empty()} when no provider is present or none matches
     */
    private Optional<McpMethodInvoker> lookupInvoker(Class<?> beanType, Method method) {
        if (invokerProviders == null || invokerProviders.isUnsatisfied()) {
            return Optional.empty();
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (McpInvokerProvider provider : invokerProviders) {
            Optional<McpMethodInvoker> found = provider.lookup(beanType, method.getName(), parameterTypes);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Object invokeViaProvider(
            Object requestId,
            Class<?> beanType,
            Method method,
            JsonObject arguments,
            McpRequestContext ctx,
            McpSession session,
            McpMethodInvoker methodInvoker) {
        Object[] args = resolveArguments(method, arguments, ctx, session, beanType);
        if (methodInvoker.resolvesInstance()) {
            return invokeAndMapExceptions(requestId, method, () -> methodInvoker.invoke(null, args));
        }
        Bean<?> bean = resolveBean(requestId, beanType);
        CreationalContext<?> creationalCtx = beanManager.createCreationalContext(bean);
        try {
            Object instance = beanManager.getReference(bean, beanType, creationalCtx);
            return invokeAndMapExceptions(requestId, method, () -> methodInvoker.invoke(instance, args));
        } finally {
            creationalCtx.release();
        }
    }

    private Object invokeViaReflection(
            Object requestId,
            Class<?> beanType,
            Method method,
            JsonObject arguments,
            McpRequestContext ctx,
            McpSession session) {
        Bean<?> bean = resolveBean(requestId, beanType);
        CreationalContext<?> creationalCtx = beanManager.createCreationalContext(bean);
        try {
            Object instance = beanManager.getReference(bean, beanType, creationalCtx);
            Object[] args = resolveArguments(method, arguments, ctx, session, beanType);
            return method.invoke(instance, args);
        } catch (InvocationTargetException e) {
            throw mapInvocationException(requestId, method, e.getCause());
        } catch (IllegalAccessException e) {
            throw new McpException(requestId, McpErrorCode.INTERNAL_ERROR, "Invocation failed: " + method.getName());
        } finally {
            creationalCtx.release();
        }
    }

    /**
     * Invokes the given call and maps its failures the same way as the reflective path: {@link McpInputRequiredSignal}
     * and {@link McpException} are rethrown unwrapped, {@link InvocationTargetException} is unwrapped before mapping,
     * and any other exception — including the checked exceptions {@code jakarta.enterprise.invoke.Invoker#invoke} is
     * declared to throw — is wrapped as {@link McpErrorCode#INTERNAL_ERROR}.
     *
     * @param requestId the JSON-RPC request ID for error reporting
     * @param method the method being invoked, for error messages
     * @param call the invocation to perform
     * @return the call's result
     */
    private Object invokeAndMapExceptions(Object requestId, Method method, InvokerCall call) {
        try {
            return call.invoke();
        } catch (InvocationTargetException e) {
            throw mapInvocationException(requestId, method, e.getCause());
        } catch (McpInputRequiredSignal | McpException e) {
            throw e;
        } catch (Exception e) {
            throw mapInvocationException(requestId, method, e);
        }
    }

    private RuntimeException mapInvocationException(Object requestId, Method method, Throwable cause) {
        if (cause instanceof McpInputRequiredSignal signal) {
            return signal;
        }
        if (cause instanceof McpException mcpException) {
            return mcpException;
        }
        return new McpException(
                requestId,
                McpErrorCode.INTERNAL_ERROR,
                "Invocation failed: " + method.getName() + " - " + cause.getMessage());
    }

    /** A single invocation to perform, abstracting over reflective and provider-supplied invokers. */
    @FunctionalInterface
    private interface InvokerCall {
        Object invoke() throws Exception;
    }

    private Bean<?> resolveBean(Object requestId, Class<?> beanType) {
        Bean<?> bean = beanManager.resolve(beanManager.getBeans(beanType));
        if (bean == null) {
            throw new McpException(
                    requestId, McpErrorCode.INTERNAL_ERROR, "CDI bean not found for: " + beanType.getName());
        }
        return bean;
    }

    private Object[] resolveArguments(
            Method method, JsonObject arguments, McpRequestContext ctx, McpSession session, Class<?> beanType) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        for (int i = 0; i < params.length; i++) {
            if (McpFrameworkTypes.isFrameworkType(params[i].getType())) {
                args[i] = apiFactory.createInstance(params[i].getType(), ctx, session, beanType);
            } else {
                String paramName = resolveParamName(params[i]);
                if (arguments != null && arguments.containsKey(paramName)) {
                    args[i] = convertJsonValue(arguments.get(paramName), params[i].getType());
                } else {
                    args[i] = getDefaultValue(params[i].getType());
                }
            }
        }
        return args;
    }

    /**
     * Resolves the wire name of a parameter: the {@code name} of its {@code @ToolArg}, {@code @PromptArg} or
     * {@code @ResourceTemplateArg} annotation when it sets one, otherwise the Java parameter name (which needs the
     * module to be compiled with {@code -parameters}, or it is {@code arg0}).
     *
     * @param param the method parameter
     * @return the name the argument is sent under
     */
    private static String resolveParamName(Parameter param) {
        ToolArg toolArg = param.getAnnotation(ToolArg.class);
        if (toolArg != null && !DEFAULT_NAME.equals(toolArg.name())) {
            return toolArg.name();
        }
        PromptArg promptArg = param.getAnnotation(PromptArg.class);
        if (promptArg != null && !DEFAULT_NAME.equals(promptArg.name())) {
            return promptArg.name();
        }
        ResourceTemplateArg templateArg = param.getAnnotation(ResourceTemplateArg.class);
        if (templateArg != null && !DEFAULT_NAME.equals(templateArg.name())) {
            return templateArg.name();
        }
        return param.getName();
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
