package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.Map;

public class McpServerCapabilities {

    private Map<String, Object> tools;
    private Map<String, Object> resources;
    private Map<String, Object> prompts;
    private Map<String, Object> logging;

    public McpServerCapabilities() {}

    public static McpServerCapabilities full() {
        McpServerCapabilities capabilities = new McpServerCapabilities();
        capabilities.tools = Map.of("listChanged", true);
        capabilities.resources = Map.of();
        capabilities.prompts = Map.of("listChanged", true);
        capabilities.logging = Map.of();
        return capabilities;
    }

    public static McpServerCapabilities withTools() {
        McpServerCapabilities capabilities = new McpServerCapabilities();
        capabilities.tools = Map.of("listChanged", true);
        return capabilities;
    }

    public Map<String, Object> getTools() {
        return tools;
    }

    public void setTools(Map<String, Object> tools) {
        this.tools = tools;
    }

    public Map<String, Object> getResources() {
        return resources;
    }

    public void setResources(Map<String, Object> resources) {
        this.resources = resources;
    }

    public Map<String, Object> getPrompts() {
        return prompts;
    }

    public void setPrompts(Map<String, Object> prompts) {
        this.prompts = prompts;
    }

    public Map<String, Object> getLogging() {
        return logging;
    }

    public void setLogging(Map<String, Object> logging) {
        this.logging = logging;
    }
}
