package dev.langchain4j.cdi.mcp.integrationtests;

/** Shared constants for MCP integration tests. */
public final class McpTestConstants {

    /** Tool name for the weather tool. */
    public static final String GET_WEATHER = "getWeather";

    /** Tool name for the greeting tool. */
    public static final String GREET = "greet";

    /** Tool name for the exotic-signature tool of {@link GreetingTool}. */
    public static final String DESCRIBE_SIGNATURE = "describeSignature";

    /** HTTP header name for the MCP session identifier. */
    public static final String MCP_SESSION_ID = "Mcp-Session-Id";

    /** URI of the application configuration resource. */
    public static final String CONFIG_APP = "config://app";

    /** JSON key for the resources array. */
    public static final String RESOURCES = "resources";

    /** Prompt name for the summarize prompt. */
    public static final String SUMMARIZE = "summarize";

    /** Tool name of {@link TaskListTool}, whose arguments carry defaults and an enum. */
    public static final String LIST_TASKS = "list_tasks";

    /** Prompt name of {@link DayPlanPrompt}, whose {@code int} argument has a default. */
    public static final String PLAN_DAY = "plan_day";

    /** Tool name for the elicitation tool. */
    public static final String ASK_NAME = "askName";

    /** MCP 2026-07-28 protocol version. */
    public static final String MODERN_VERSION = "2026-07-28";

    /** MCP legacy protocol version recognized by the langchain4j client. */
    public static final String LEGACY_CLIENT_VERSION = "2025-11-25";

    private McpTestConstants() {}
}
