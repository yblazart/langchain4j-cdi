package dev.langchain4j.cdi.mcp.server.protocol;

import jakarta.json.JsonObject;

public class McpToolCallParams {

    private String name;
    private JsonObject arguments;

    public McpToolCallParams() {}

    public McpToolCallParams(String name, JsonObject arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JsonObject getArguments() {
        return arguments;
    }

    public void setArguments(JsonObject arguments) {
        this.arguments = arguments;
    }
}
