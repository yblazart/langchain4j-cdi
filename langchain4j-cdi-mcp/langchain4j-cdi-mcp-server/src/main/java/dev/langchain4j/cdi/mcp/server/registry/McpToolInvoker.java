package dev.langchain4j.cdi.mcp.server.registry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@ApplicationScoped
public class McpToolInvoker {

    @Inject
    McpBeanInvoker beanInvoker;

    public Object invoke(McpToolDescriptor descriptor, JsonObject arguments) {
        return beanInvoker.invoke(descriptor.getBeanType(), descriptor.getMethod(), arguments);
    }
}
