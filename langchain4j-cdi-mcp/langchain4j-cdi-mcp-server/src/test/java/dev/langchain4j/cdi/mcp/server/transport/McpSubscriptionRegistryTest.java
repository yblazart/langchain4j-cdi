package dev.langchain4j.cdi.mcp.server.transport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcNotification;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class McpSubscriptionRegistryTest {

    final McpSubscriptionRegistry registry = new McpSubscriptionRegistry();

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    static List<JsonObject> events(ByteArrayOutputStream out) {
        return Arrays.stream(out.toString(StandardCharsets.UTF_8).split("\n\n"))
                .filter(block -> block.startsWith("event: message\ndata: "))
                .map(block -> {
                    try (JsonReader reader =
                            Json.createReader(new StringReader(block.substring("event: message\ndata: ".length())))) {
                        return reader.readObject();
                    }
                })
                .toList();
    }

    McpListenSubscription open(Object id, ByteArrayOutputStream out, JsonObject requested) {
        return registry.open(
                id, McpNotificationFilter.from(requested), new McpSseResponseChannel(out, new AtomicBoolean()));
    }

    @Test
    void acknowledgementIsFirstMessage() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        open(1L, out, Json.createObjectBuilder().add("toolsListChanged", true).build());

        JsonObject ack = events(out).get(0);
        assertThat(ack.getString("method")).isEqualTo("notifications/subscriptions/acknowledged");
        assertThat(ack.getJsonObject("params").getJsonObject("_meta").getInt("io.modelcontextprotocol/subscriptionId"))
                .isEqualTo(1);
        assertThat(ack.getJsonObject("params").getJsonObject("notifications").getBoolean("toolsListChanged"))
                .isTrue();
    }

    @Test
    void dispatchDeliversOnlyRequestedNotificationsTaggedWithSubscriptionId() {
        ByteArrayOutputStream tools = new ByteArrayOutputStream();
        ByteArrayOutputStream prompts = new ByteArrayOutputStream();
        open(
                "listen-1",
                tools,
                Json.createObjectBuilder().add("toolsListChanged", true).build());
        open(
                "listen-2",
                prompts,
                Json.createObjectBuilder().add("promptsListChanged", true).build());

        registry.dispatch(JsonRpcNotification.toolsListChanged());

        assertThat(events(tools)).hasSize(2);
        JsonObject delivered = events(tools).get(1);
        assertThat(delivered.getString("method")).isEqualTo("notifications/tools/list_changed");
        assertThat(delivered
                        .getJsonObject("params")
                        .getJsonObject("_meta")
                        .getString("io.modelcontextprotocol/subscriptionId"))
                .isEqualTo("listen-1");
        assertThat(events(prompts)).hasSize(1);
    }

    @Test
    void resourceUpdatesAreFilteredByUri() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        open(
                2L,
                out,
                Json.createObjectBuilder()
                        .add("resourceSubscriptions", Json.createArrayBuilder().add("config://app"))
                        .build());

        registry.dispatch(JsonRpcNotification.resourceUpdated("other://x"));
        registry.dispatch(JsonRpcNotification.resourceUpdated("config://app"));

        assertThat(events(out)).hasSize(2);
        assertThat(events(out).get(1).getJsonObject("params").getString("uri")).isEqualTo("config://app");
    }

    @Test
    void brokenStreamsAreRemoved() {
        AtomicBoolean clientGone = new AtomicBoolean();
        OutputStream breaking = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                if (clientGone.get()) {
                    throw new IOException("closed");
                }
            }
        };
        McpListenSubscription subscription = registry.open(
                3L,
                McpNotificationFilter.from(
                        Json.createObjectBuilder().add("toolsListChanged", true).build()),
                new McpSseResponseChannel(breaking, new AtomicBoolean()));

        clientGone.set(true);
        registry.dispatch(JsonRpcNotification.toolsListChanged());

        assertThat(registry.size()).isZero();
        assertThat(subscription.isClosed()).isTrue();
    }

    @Test
    void shutdownEndsSubscriptionsGracefully() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        McpListenSubscription subscription = open(
                4L,
                out,
                Json.createObjectBuilder().add("toolsListChanged", true).build());

        registry.shutdown();
        subscription.awaitClose();

        JsonObject last = events(out).get(events(out).size() - 1);
        assertThat(last.getInt("id")).isEqualTo(4);
        assertThat(last.getJsonObject("result").getString("resultType")).isEqualTo("complete");
    }
}
