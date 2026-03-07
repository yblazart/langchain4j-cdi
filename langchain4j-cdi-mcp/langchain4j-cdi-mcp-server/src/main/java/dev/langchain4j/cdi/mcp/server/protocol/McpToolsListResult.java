package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

public class McpToolsListResult {

    private List<McpToolWireFormat> tools;
    private String nextCursor;

    public McpToolsListResult() {}

    public McpToolsListResult(List<McpToolWireFormat> tools) {
        this.tools = tools;
    }

    public McpToolsListResult(List<McpToolWireFormat> tools, String nextCursor) {
        this.tools = tools;
        this.nextCursor = nextCursor;
    }

    public List<McpToolWireFormat> getTools() {
        return tools;
    }

    public void setTools(List<McpToolWireFormat> tools) {
        this.tools = tools;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
