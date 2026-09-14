package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

class McpNotificationFilterTest {

    @Test
    void parsesRequestedNotifications() {
        JsonObject requested = Json.createObjectBuilder()
                .add("toolsListChanged", true)
                .add("resourceSubscriptions", Json.createArrayBuilder().add("config://app"))
                .build();

        McpNotificationFilter filter = McpNotificationFilter.from(requested);

        assertThat(filter.toolsListChanged()).isTrue();
        assertThat(filter.promptsListChanged()).isFalse();
        assertThat(filter.resourceSubscriptions()).containsExactly("config://app");
        assertThat(filter.toJson()).isEqualTo(requested);
    }

    @Test
    void acceptsOnlyRequestedTypes() {
        McpNotificationFilter filter = McpNotificationFilter.from(Json.createObjectBuilder()
                .add("promptsListChanged", true)
                .add("resourceSubscriptions", Json.createArrayBuilder().add("config://app"))
                .build());

        assertThat(filter.accepts("notifications/prompts/list_changed", JsonValue.EMPTY_JSON_OBJECT))
                .isTrue();
        assertThat(filter.accepts("notifications/tools/list_changed", JsonValue.EMPTY_JSON_OBJECT))
                .isFalse();
        assertThat(filter.accepts(
                        "notifications/resources/updated",
                        Json.createObjectBuilder().add("uri", "config://app").build()))
                .isTrue();
        assertThat(filter.accepts(
                        "notifications/resources/updated",
                        Json.createObjectBuilder().add("uri", "other://x").build()))
                .isFalse();
        assertThat(filter.accepts("notifications/progress", JsonValue.EMPTY_JSON_OBJECT))
                .isFalse();
    }

    @Test
    void emptyRequestAcceptsNothing() {
        McpNotificationFilter filter = McpNotificationFilter.from(JsonValue.EMPTY_JSON_OBJECT);

        assertThat(filter.toJson()).isEmpty();
        assertThat(filter.accepts("notifications/tools/list_changed", JsonValue.EMPTY_JSON_OBJECT))
                .isFalse();
    }
}
