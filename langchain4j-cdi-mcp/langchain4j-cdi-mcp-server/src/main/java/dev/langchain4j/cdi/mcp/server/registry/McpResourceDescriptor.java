package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.McpResource;
import java.lang.reflect.Method;

public class McpResourceDescriptor {

    private final String uri;
    private final String name;
    private final String description;
    private final String mimeType;
    private final Class<?> beanType;
    private final Method method;

    public McpResourceDescriptor(
            String uri, String name, String description, String mimeType, Class<?> beanType, Method method) {
        this.uri = uri;
        this.name = name;
        this.description = description;
        this.mimeType = mimeType;
        this.beanType = beanType;
        this.method = method;
    }

    public static McpResourceDescriptor fromMethod(Class<?> beanClass, Method method) {
        McpResource annotation = method.getAnnotation(McpResource.class);
        String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
        return new McpResourceDescriptor(
                annotation.uri(), name, annotation.description(), annotation.mimeType(), beanClass, method);
    }

    public String getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Class<?> getBeanType() {
        return beanType;
    }

    public Method getMethod() {
        return method;
    }
}
