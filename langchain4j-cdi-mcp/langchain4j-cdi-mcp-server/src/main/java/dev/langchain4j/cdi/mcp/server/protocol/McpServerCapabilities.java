package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.Map;

public class McpServerCapabilities {

    private Map<String, Object> tools;

    public McpServerCapabilities() {}

    public static McpServerCapabilities withTools() {
        McpServerCapabilities capabilities = new McpServerCapabilities();
        capabilities.tools = Map.of("listChanged", false);
        return capabilities;
    }

    public Map<String, Object> getTools() {
        return tools;
    }

    public void setTools(Map<String, Object> tools) {
        this.tools = tools;
    }
}
