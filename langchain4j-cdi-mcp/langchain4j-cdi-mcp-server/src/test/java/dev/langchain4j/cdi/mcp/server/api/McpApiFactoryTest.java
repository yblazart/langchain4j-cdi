package dev.langchain4j.cdi.mcp.server.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.cdi.mcp.server.logging.McpLogLevel;
import dev.langchain4j.cdi.mcp.server.logging.McpLogger;
import dev.langchain4j.cdi.mcp.server.protocol.McpJsonSerializer;
import dev.langchain4j.cdi.mcp.server.transport.*;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.progress.Progress;

class McpApiFactoryTest {

    McpApiFactory factory;
    final List<Object> sent = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        factory = new McpApiFactory();
        inject("mcpLogger", mock(McpLogger.class));
        inject("progressReporter", mock(McpProgressReporter.class));
        inject("rootsManager", new McpRootsManager());
        inject("samplingManager", new McpSamplingManager());
        inject("elicitationManager", new McpElicitationManager());
        inject("serverRequestManager", mock(McpServerRequestManager.class));
    }

    private void inject(String name, Object value) throws Exception {
        Field field = McpApiFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(factory, value);
    }

    private McpRequestContext modernContext(McpLogLevel level) {
        McpProtocolContext protocol = new McpProtocolContext(
                McpEra.MODERN,
                "2026-07-28",
                Json.createObjectBuilder()
                        .add("elicitation", JsonValue.EMPTY_JSON_OBJECT)
                        .build(),
                null,
                level);
        McpClientRequester requester = new McpClientRequester() {
            @Override
            public boolean supports(String capability) {
                return protocol.hasClientCapability(capability);
            }

            @Override
            public jakarta.json.JsonObject request(
                    String method, java.util.Map<String, Object> params, java.time.Duration timeout) {
                return JsonValue.EMPTY_JSON_OBJECT;
            }
        };
        return new McpRequestContext(null, 1, "tok", new AtomicBoolean(), protocol, requester, sent::add);
    }

    @Test
    void modernLogGoesToRequestChannel() {
        McpLog log = (McpLog) factory.createInstance(McpLog.class, modernContext(McpLogLevel.info), null, String.class);

        log.info("hello {}", "world");

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).toString()).contains("hello world");
    }

    @Test
    void modernProgressGoesToRequestChannel() {
        Progress progress = (Progress) factory.createInstance(Progress.class, modernContext(null), null, String.class);

        progress.notificationBuilder().setProgress(1).setTotal(2).build().sendAndForget();

        assertThat(sent).hasSize(1);
        assertThat(McpJsonSerializer.toJsonValue(sent.get(0)).toString()).contains("notifications/progress");
    }

    @Test
    void modernElicitationSupportFollowsRequestCapabilities() {
        Elicitation elicitation =
                (Elicitation) factory.createInstance(Elicitation.class, modernContext(null), null, String.class);
        Sampling sampling = (Sampling) factory.createInstance(Sampling.class, modernContext(null), null, String.class);

        assertThat(elicitation.isSupported()).isTrue();
        assertThat(sampling.isSupported()).isFalse();
    }

    @Test
    void modernConnectionHasNoSession() {
        McpConnection connection =
                (McpConnection) factory.createInstance(McpConnection.class, modernContext(null), null, String.class);

        assertThat(connection.status()).isEqualTo(McpConnection.Status.IN_OPERATION);
        assertThat(connection.initialRequest().getString("protocolVersion")).isEqualTo("2026-07-28");
    }
}
