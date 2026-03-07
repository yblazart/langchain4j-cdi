package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.McpPrompt;
import dev.langchain4j.cdi.mcp.server.McpPromptArg;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

public class McpPromptDescriptor {

    private final String name;
    private final String description;
    private final List<PromptArgument> arguments;
    private final Class<?> beanType;
    private final Method method;

    public McpPromptDescriptor(
            String name, String description, List<PromptArgument> arguments, Class<?> beanType, Method method) {
        this.name = name;
        this.description = description;
        this.arguments = arguments;
        this.beanType = beanType;
        this.method = method;
    }

    public static McpPromptDescriptor fromMethod(Class<?> beanClass, Method method) {
        McpPrompt annotation = method.getAnnotation(McpPrompt.class);
        String name = annotation.name().isEmpty() ? method.getName() : annotation.name();

        List<PromptArgument> args = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            McpPromptArg argAnnotation = param.getAnnotation(McpPromptArg.class);
            String argDescription = argAnnotation != null ? argAnnotation.value() : "";
            boolean required = argAnnotation == null || argAnnotation.required();
            args.add(new PromptArgument(param.getName(), argDescription, required));
        }

        return new McpPromptDescriptor(name, annotation.value(), args, beanClass, method);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<PromptArgument> getArguments() {
        return arguments;
    }

    public Class<?> getBeanType() {
        return beanType;
    }

    public Method getMethod() {
        return method;
    }

    public record PromptArgument(String name, String description, boolean required) {}
}
