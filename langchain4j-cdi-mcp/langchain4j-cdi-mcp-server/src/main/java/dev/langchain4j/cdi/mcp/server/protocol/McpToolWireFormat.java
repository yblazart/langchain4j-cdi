package dev.langchain4j.cdi.mcp.server.protocol;

import jakarta.json.JsonObject;

public class McpToolWireFormat {

    private String name;
    private String description;
    private JsonObject inputSchema;

    public McpToolWireFormat() {}

    public McpToolWireFormat(String name, String description, JsonObject inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public JsonObject getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(JsonObject inputSchema) {
        this.inputSchema = inputSchema;
    }
}
