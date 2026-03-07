package dev.langchain4j.cdi.mcp.server.registry;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class McpPromptRegistry {

    private final Map<String, McpPromptDescriptor> prompts = new ConcurrentHashMap<>();

    public void register(McpPromptDescriptor descriptor) {
        prompts.put(descriptor.getName(), descriptor);
    }

    public Collection<McpPromptDescriptor> listPrompts() {
        return Collections.unmodifiableCollection(prompts.values());
    }

    public Optional<McpPromptDescriptor> findPrompt(String name) {
        return Optional.ofNullable(prompts.get(name));
    }

    public int size() {
        return prompts.size();
    }
}
