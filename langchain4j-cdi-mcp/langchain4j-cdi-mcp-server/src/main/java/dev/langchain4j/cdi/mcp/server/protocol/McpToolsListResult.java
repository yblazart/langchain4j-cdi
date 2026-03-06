package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

public class McpToolsListResult {

    private List<McpToolWireFormat> tools;

    public McpToolsListResult() {}

    public McpToolsListResult(List<McpToolWireFormat> tools) {
        this.tools = tools;
    }

    public List<McpToolWireFormat> getTools() {
        return tools;
    }

    public void setTools(List<McpToolWireFormat> tools) {
        this.tools = tools;
    }
}
