package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

public class McpPromptsListResult {

    private List<McpPromptWireFormat> prompts;
    private String nextCursor;

    public McpPromptsListResult() {}

    public McpPromptsListResult(List<McpPromptWireFormat> prompts) {
        this.prompts = prompts;
    }

    public McpPromptsListResult(List<McpPromptWireFormat> prompts, String nextCursor) {
        this.prompts = prompts;
        this.nextCursor = nextCursor;
    }

    public List<McpPromptWireFormat> getPrompts() {
        return prompts;
    }

    public void setPrompts(List<McpPromptWireFormat> prompts) {
        this.prompts = prompts;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
