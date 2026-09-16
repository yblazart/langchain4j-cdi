package dev.langchain4j.cdi.mcp.server.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.cdi.mcp.server.transport.McpClientRequester;
import dev.langchain4j.cdi.mcp.server.transport.McpRootsManager;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CdiRootsTest {

    static final class KeyCapturingRequester implements McpClientRequester {
        String key;
        boolean requestCalled;

        @Override
        public boolean supports(String capability) {
            return true;
        }

        @Override
        public JsonObject request(String method, Map<String, Object> params, Duration timeout) {
            return request(method, params, timeout, null);
        }

        @Override
        public JsonObject request(String method, Map<String, Object> params, Duration timeout, String key) {
            this.key = key;
            this.requestCalled = true;
            return Json.createObjectBuilder()
                    .add("roots", Json.createArrayBuilder())
                    .build();
        }
    }

    @Test
    void keyedListAndAwaitSendsTheGivenKey() {
        KeyCapturingRequester requester = new KeyCapturingRequester();
        Roots roots = new CdiRoots(requester, new McpRootsManager());

        roots.listAndAwait("workspace-roots");

        assertThat(requester.requestCalled).isTrue();
        assertThat(requester.key).isEqualTo("workspace-roots");
    }

    @Test
    void unkeyedListAndAwaitStillSendsNullKey() {
        KeyCapturingRequester requester = new KeyCapturingRequester();
        Roots roots = new CdiRoots(requester, new McpRootsManager());

        roots.listAndAwait();

        assertThat(requester.requestCalled).isTrue();
        assertThat(requester.key).isNull();
    }

    @Test
    void blankKeyIsRejected() {
        Roots roots = new CdiRoots(new KeyCapturingRequester(), new McpRootsManager());

        assertThatThrownBy(() -> roots.listAndAwait("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key");
    }
}
