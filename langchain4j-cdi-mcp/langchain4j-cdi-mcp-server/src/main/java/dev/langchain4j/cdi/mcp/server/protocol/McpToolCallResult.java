package dev.langchain4j.cdi.mcp.server.protocol;

import java.util.List;

public class McpToolCallResult {

    private List<McpContent> content;
    private boolean isError;

    public McpToolCallResult() {}

    public McpToolCallResult(List<McpContent> content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    public static McpToolCallResult text(String text) {
        return new McpToolCallResult(List.of(new McpContent("text", text)), false);
    }

    public static McpToolCallResult error(String message) {
        return new McpToolCallResult(List.of(new McpContent("text", message)), true);
    }

    public static McpToolCallResult empty() {
        return new McpToolCallResult(List.of(), false);
    }

    public List<McpContent> getContent() {
        return content;
    }

    public void setContent(List<McpContent> content) {
        this.content = content;
    }

    public boolean isIsError() {
        return isError;
    }

    public void setIsError(boolean isError) {
        this.isError = isError;
    }
}
