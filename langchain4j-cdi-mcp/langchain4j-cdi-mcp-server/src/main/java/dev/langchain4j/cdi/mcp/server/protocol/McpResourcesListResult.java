package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

public class McpResourcesListResult {

    private List<McpResourceWireFormat> resources;
    private String nextCursor;

    public McpResourcesListResult() {}

    public McpResourcesListResult(List<McpResourceWireFormat> resources) {
        this.resources = resources;
    }

    public McpResourcesListResult(List<McpResourceWireFormat> resources, String nextCursor) {
        this.resources = resources;
        this.nextCursor = nextCursor;
    }

    public List<McpResourceWireFormat> getResources() {
        return resources;
    }

    public void setResources(List<McpResourceWireFormat> resources) {
        this.resources = resources;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
