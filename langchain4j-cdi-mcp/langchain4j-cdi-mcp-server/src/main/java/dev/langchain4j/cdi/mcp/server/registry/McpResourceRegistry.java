package dev.langchain4j.cdi.mcp.server.registry;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class McpResourceRegistry {

    private final Map<String, McpResourceDescriptor> resources = new ConcurrentHashMap<>();

    public void register(McpResourceDescriptor descriptor) {
        resources.put(descriptor.getUri(), descriptor);
    }

    public Collection<McpResourceDescriptor> listResources() {
        return Collections.unmodifiableCollection(resources.values());
    }

    public Optional<McpResourceDescriptor> findResource(String uri) {
        return Optional.ofNullable(resources.get(uri));
    }

    public int size() {
        return resources.size();
    }
}
