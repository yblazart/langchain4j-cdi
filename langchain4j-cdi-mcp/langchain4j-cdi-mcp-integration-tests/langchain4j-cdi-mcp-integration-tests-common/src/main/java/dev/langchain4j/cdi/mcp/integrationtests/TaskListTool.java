package dev.langchain4j.cdi.mcp.integrationtests;

import jakarta.enterprise.context.ApplicationScoped;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/** MCP tool whose arguments carry defaults and an enum (langchain4j-cdi#298). */
@ApplicationScoped
public class TaskListTool {

    /** Creates a new instance. */
    public TaskListTool() {}

    /** A task priority, advertised and bound by constant name. */
    public enum Priority {
        /** Low priority. */
        LOW,
        /** Medium priority. */
        MEDIUM,
        /** High priority. */
        HIGH
    }

    /**
     * Echoes the arguments it was bound with.
     *
     * @param limit maximum number of tasks, defaults to 20
     * @param includeDone whether done tasks are listed, defaults to {@code true}
     * @param priority the priority to list, defaults to {@link Priority#MEDIUM}
     * @return the bound values, formatted
     */
    @Tool(name = McpTestConstants.LIST_TASKS, description = "List tasks")
    public String listTasks(
            @ToolArg(name = "limit", description = "Max results", defaultValue = "20") int limit,
            @ToolArg(name = "includeDone", description = "Include done tasks", defaultValue = "true")
                    boolean includeDone,
            @ToolArg(name = "priority", description = "Priority", defaultValue = "MEDIUM") Priority priority) {
        return "limit=" + limit + ", includeDone=" + includeDone + ", priority=" + priority;
    }
}
