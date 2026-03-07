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
    private final Map<String, McpResourceTemplateDescriptor> templates = new ConcurrentHashMap<>();

    public void register(McpResourceDescriptor descriptor) {
        resources.put(descriptor.getUri(), descriptor);
    }

    public void registerTemplate(McpResourceTemplateDescriptor descriptor) {
        templates.put(descriptor.getUriTemplate(), descriptor);
    }

    public Collection<McpResourceDescriptor> listResources() {
        return Collections.unmodifiableCollection(resources.values());
    }

    public Collection<McpResourceTemplateDescriptor> listTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    public Optional<McpResourceDescriptor> findResource(String uri) {
        return Optional.ofNullable(resources.get(uri));
    }

    public Optional<McpResourceTemplateDescriptor> findTemplate(String uriTemplate) {
        return Optional.ofNullable(templates.get(uriTemplate));
    }

    public int size() {
        return resources.size();
    }

    public int templateSize() {
        return templates.size();
    }
}
