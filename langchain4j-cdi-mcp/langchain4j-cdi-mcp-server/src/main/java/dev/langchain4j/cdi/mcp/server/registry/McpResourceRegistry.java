package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcNotification;
import dev.langchain4j.cdi.mcp.server.transport.McpNotificationBroadcaster;
import dev.langchain4j.cdi.mcp.server.transport.McpResourceSubscriptionManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-scoped registry of MCP resource and resource-template descriptors. Manages registration, lookup, and
 * broadcasts list-changed and resource-updated notifications to connected clients.
 */
@ApplicationScoped
public class McpResourceRegistry {

    private final Map<String, McpResourceDescriptor> resources = new ConcurrentHashMap<>();
    private final Map<String, McpResourceTemplateDescriptor> templates = new ConcurrentHashMap<>();

    @Inject
    McpNotificationBroadcaster broadcaster;

    @Inject
    McpResourceSubscriptionManager subscriptionManager;

    /** CDI-required default constructor. */
    public McpResourceRegistry() {}

    /**
     * Registers a resource descriptor. Broadcasts a list-changed notification if this is a new resource.
     *
     * @param descriptor the resource descriptor to register
     */
    public void register(McpResourceDescriptor descriptor) {
        McpResourceDescriptor previous = resources.put(descriptor.getUri(), descriptor);
        if (previous == null) {
            notifyListChanged();
        }
    }

    /**
     * Removes a resource by URI. Broadcasts a list-changed notification if the resource existed.
     *
     * @param uri the resource URI
     * @return {@code true} if a resource was removed
     */
    public boolean unregister(String uri) {
        McpResourceDescriptor removed = resources.remove(uri);
        if (removed != null) {
            notifyListChanged();
        }
        return removed != null;
    }

    /**
     * Registers a resource template descriptor. Broadcasts a list-changed notification if this is a new template.
     *
     * @param descriptor the resource template descriptor to register
     */
    public void registerTemplate(McpResourceTemplateDescriptor descriptor) {
        McpResourceTemplateDescriptor previous = templates.put(descriptor.getUriTemplate(), descriptor);
        if (previous == null) {
            notifyListChanged();
        }
    }

    /**
     * Sends a resource-updated notification to all sessions subscribed to the given URI.
     *
     * @param uri the URI of the updated resource
     */
    public void notifyResourceUpdated(String uri) {
        if (broadcaster == null || subscriptionManager == null) {
            return;
        }
        JsonRpcNotification notification = JsonRpcNotification.resourceUpdated(uri);
        for (String sessionId : subscriptionManager.getSubscribedSessions(uri)) {
            broadcaster.sendToSession(sessionId, notification);
        }
        broadcaster.dispatchToSubscriptions(notification);
    }

    private void notifyListChanged() {
        if (broadcaster != null && broadcaster.connectedStreamCount() > 0) {
            broadcaster.broadcast(JsonRpcNotification.resourcesListChanged());
        }
    }

    /**
     * Returns an unmodifiable view of all registered resource descriptors.
     *
     * @return the registered resources
     */
    public Collection<McpResourceDescriptor> listResources() {
        return Collections.unmodifiableCollection(resources.values());
    }

    /**
     * Returns an unmodifiable view of all registered resource template descriptors.
     *
     * @return the registered resource templates
     */
    public Collection<McpResourceTemplateDescriptor> listTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    /**
     * Finds a resource descriptor by URI.
     *
     * @param uri the resource URI
     * @return an {@link Optional} containing the descriptor, or empty if not found
     */
    public Optional<McpResourceDescriptor> findResource(String uri) {
        return Optional.ofNullable(resources.get(uri));
    }

    /**
     * Finds a resource template descriptor by URI template.
     *
     * @param uriTemplate the URI template string
     * @return an {@link Optional} containing the descriptor, or empty if not found
     */
    public Optional<McpResourceTemplateDescriptor> findTemplate(String uriTemplate) {
        return Optional.ofNullable(templates.get(uriTemplate));
    }

    /**
     * Finds the resource template that a concrete URI is an instance of, and extracts its variables.
     *
     * <p>Only useful once an exact {@link #findResource(String)} lookup has come up empty. When several templates match
     * the URI, the most specific one wins: fewest variables first, then the longest URI template.
     *
     * @param uri the requested resource URI
     * @return the matching template and its variables, or an empty {@link Optional} if no template matches
     */
    public Optional<TemplateMatch> matchTemplate(String uri) {
        if (uri == null) {
            return Optional.empty();
        }
        TemplateMatch best = null;
        for (McpResourceTemplateDescriptor template : templates.values()) {
            Optional<Map<String, String>> variables = template.match(uri);
            if (variables.isEmpty()) {
                continue;
            }
            TemplateMatch candidate = new TemplateMatch(template, variables.get());
            if (best == null || isMoreSpecific(candidate.template(), best.template())) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isMoreSpecific(McpResourceTemplateDescriptor candidate, McpResourceTemplateDescriptor best) {
        int variables =
                candidate.getVariableNames().size() - best.getVariableNames().size();
        if (variables != 0) {
            return variables < 0;
        }
        return candidate.getUriTemplate().length() > best.getUriTemplate().length();
    }

    /**
     * A resource template matched against a concrete URI.
     *
     * @param template the matched resource template
     * @param variables the template variables extracted from the URI, by name
     */
    public record TemplateMatch(McpResourceTemplateDescriptor template, Map<String, String> variables) {}

    /**
     * Returns the number of registered resources.
     *
     * @return the resource count
     */
    public int size() {
        return resources.size();
    }

    /**
     * Returns the number of registered resource templates.
     *
     * @return the template count
     */
    public int templateSize() {
        return templates.size();
    }
}
