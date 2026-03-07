package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

public class McpResourceTemplatesListResult {

    private List<McpResourceTemplateWireFormat> resourceTemplates;
    private String nextCursor;

    public McpResourceTemplatesListResult() {}

    public McpResourceTemplatesListResult(List<McpResourceTemplateWireFormat> resourceTemplates) {
        this.resourceTemplates = resourceTemplates;
    }

    public List<McpResourceTemplateWireFormat> getResourceTemplates() {
        return resourceTemplates;
    }

    public void setResourceTemplates(List<McpResourceTemplateWireFormat> resourceTemplates) {
        this.resourceTemplates = resourceTemplates;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
