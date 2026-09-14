package dev.langchain4j.cdi.mcp.server.transport;

import java.util.Map;

/**
 * Control-flow signal raised by a stateless client requester when the answer to a client request is not yet known.
 * Caught by the modern handler and turned into an {@code input_required} result.
 */
public final class McpInputRequiredSignal extends RuntimeException {

    private final String key;
    private final String method;
    private final transient Map<String, Object> params;

    public McpInputRequiredSignal(String key, String method, Map<String, Object> params) {
        super("Client input required: " + method, null, false, false);
        this.key = key;
        this.method = method;
        this.params = params;
    }

    public String key() {
        return key;
    }

    public String method() {
        return method;
    }

    public Map<String, Object> params() {
        return params;
    }
}
