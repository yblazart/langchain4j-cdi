package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.McpResourceTemplate;
import java.lang.reflect.Method;

public class McpResourceTemplateDescriptor {

    private final String uriTemplate;
    private final String name;
    private final String description;
    private final String mimeType;
    private final Class<?> beanType;
    private final Method method;

    public McpResourceTemplateDescriptor(
            String uriTemplate, String name, String description, String mimeType, Class<?> beanType, Method method) {
        this.uriTemplate = uriTemplate;
        this.name = name;
        this.description = description;
        this.mimeType = mimeType;
        this.beanType = beanType;
        this.method = method;
    }

    public static McpResourceTemplateDescriptor fromMethod(Class<?> beanClass, Method method) {
        McpResourceTemplate annotation = method.getAnnotation(McpResourceTemplate.class);
        String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
        return new McpResourceTemplateDescriptor(
                annotation.uriTemplate(), name, annotation.description(), annotation.mimeType(), beanClass, method);
    }

    public String getUriTemplate() {
        return uriTemplate;
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
