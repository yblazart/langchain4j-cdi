package dev.langchain4j.cdi.mcp.server.protocol;

/**
 * A message within an MCP prompt response. Each message has a role ("user" or "assistant") and content. Can be returned
 * from {@link org.mcp_java.annotations.prompts.Prompt @Prompt} methods as {@code List<McpPromptMessage>}.
 */
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
