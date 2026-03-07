package dev.langchain4j.cdi.mcp.server.protocol;

public class McpPromptMessage {

    private String role;
    private McpContent content;

    public McpPromptMessage() {}

    public McpPromptMessage(String role, McpContent content) {
        this.role = role;
        this.content = content;
    }

    public static McpPromptMessage user(String text) {
        return new McpPromptMessage("user", McpContent.text(text));
    }

    public static McpPromptMessage assistant(String text) {
        return new McpPromptMessage("assistant", McpContent.text(text));
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public McpContent getContent() {
        return content;
    }

    public void setContent(McpContent content) {
        this.content = content;
    }
}
