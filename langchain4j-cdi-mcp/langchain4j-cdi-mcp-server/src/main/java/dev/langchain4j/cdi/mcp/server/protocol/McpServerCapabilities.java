package dev.langchain4j.cdi.mcp.server.protocol;

/** Server capabilities advertised during MCP initialization. */
public record McpServerCapabilities(
        ToolsCapability tools,
        ResourcesCapability resources,
        PromptsCapability prompts,
        LoggingCapability logging,
        CompletionsCapability completions) {

    /** @deprecated use the canonical constructor including {@code completions}. */
    @Deprecated
    public McpServerCapabilities(
            ToolsCapability tools,
            ResourcesCapability resources,
            PromptsCapability prompts,
            LoggingCapability logging) {
        this(tools, resources, prompts, logging, null);
    }

    /** Indicates the server supports tools, optionally with list-change notifications. */
    public record ToolsCapability(boolean listChanged) {}

    /** Indicates the server supports resources, optionally with subscriptions and list-change notifications. */
    public record ResourcesCapability(boolean subscribe, boolean listChanged) {}

    /** Indicates the server supports prompts, optionally with list-change notifications. */
    public record PromptsCapability(boolean listChanged) {}

    /** Indicates the server supports logging. Presence alone signals support; no fields needed. */
    public record LoggingCapability() {
        public static final LoggingCapability INSTANCE = new LoggingCapability();
    }

    /** Indicates the server supports completions. Presence alone signals support; no fields needed. */
    public record CompletionsCapability() {
        public static final CompletionsCapability INSTANCE = new CompletionsCapability();
    }
}
