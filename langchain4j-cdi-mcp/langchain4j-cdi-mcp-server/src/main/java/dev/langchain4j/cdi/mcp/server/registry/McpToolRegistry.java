package dev.langchain4j.cdi.mcp.server.registry;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class McpToolRegistry {

    private final Map<String, McpToolDescriptor> tools = new ConcurrentHashMap<>();

    public void register(McpToolDescriptor descriptor) {
        tools.put(descriptor.getName(), descriptor);
    }

    public Collection<McpToolDescriptor> listTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public Optional<McpToolDescriptor> findTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public int size() {
        return tools.size();
    }
}
