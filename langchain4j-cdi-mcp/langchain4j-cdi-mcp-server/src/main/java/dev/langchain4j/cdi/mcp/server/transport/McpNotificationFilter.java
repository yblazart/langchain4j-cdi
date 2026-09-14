package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Notification types a {@code subscriptions/listen} client opted in to. */
public record McpNotificationFilter(
        boolean toolsListChanged,
        boolean promptsListChanged,
        boolean resourcesListChanged,
        Set<String> resourceSubscriptions) {

    /**
     * Parses the {@code notifications} object of a {@code subscriptions/listen} request.
     *
     * @param requested the requested notification types
     * @return the parsed filter
     */
    public static McpNotificationFilter from(JsonObject requested) {
        Set<String> uris = new LinkedHashSet<>();
        if (requested.get("resourceSubscriptions") instanceof JsonArray array) {
            array.forEach(value -> {
                if (value instanceof JsonString s) {
                    uris.add(s.getString());
                }
            });
        }
        return new McpNotificationFilter(
                requested.getBoolean("toolsListChanged", false),
                requested.getBoolean("promptsListChanged", false),
                requested.getBoolean("resourcesListChanged", false),
                Collections.unmodifiableSet(uris));
    }

    /**
     * Returns the acknowledged subset, omitting types not requested.
     *
     * @return the acknowledged subset
     */
    public JsonObject toJson() {
        JsonObjectBuilder json = Json.createObjectBuilder();
        if (toolsListChanged) {
            json.add("toolsListChanged", true);
        }
        if (promptsListChanged) {
            json.add("promptsListChanged", true);
        }
        if (resourcesListChanged) {
            json.add("resourcesListChanged", true);
        }
        if (!resourceSubscriptions.isEmpty()) {
            json.add("resourceSubscriptions", Json.createArrayBuilder(resourceSubscriptions));
        }
        return json.build();
    }

    /**
     * Returns whether this filter accepts the given notification.
     *
     * @param method the notification's JSON-RPC method
     * @param params the notification's parameters
     * @return {@code true} if the client opted in to this notification
     */
    public boolean accepts(String method, JsonObject params) {
        return switch (method) {
            case "notifications/tools/list_changed" -> toolsListChanged;
            case "notifications/prompts/list_changed" -> promptsListChanged;
            case "notifications/resources/list_changed" -> resourcesListChanged;
            case "notifications/resources/updated" -> resourceSubscriptions.contains(params.getString("uri", ""));
            default -> false;
        };
    }
}
