package dev.langchain4j.cdi.mcp.server.fixtures;

import java.util.List;
import java.util.Locale;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/** Fixtures for argument binding (langchain4j-cdi#298): defaults, enums, and strictly typed JSON arguments. */
public class ArgumentBindingTool {

    /** A priority whose {@code toString()} differs from its {@code name()}, so tests can tell which one is used. */
    public enum Priority {
        LOW,
        MEDIUM,
        HIGH;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    @Tool(name = "list_tasks", description = "List tasks")
    public String listTasks(
            @ToolArg(name = "limit", description = "Max results", defaultValue = "20") Integer limit,
            @ToolArg(name = "pageSize", description = "Page size", defaultValue = "10") int pageSize,
            @ToolArg(name = "includeDone", description = "Include done tasks", defaultValue = "true")
                    boolean includeDone,
            @ToolArg(name = "priority", description = "Priority", defaultValue = "MEDIUM") Priority priority) {
        return "limit=" + limit + ", pageSize=" + pageSize + ", includeDone=" + includeDone + ", priority="
                + priority.name();
    }

    @Tool(name = "by_priority", description = "List tasks of one priority")
    public String byPriority(@ToolArg(name = "priority", description = "Priority") Priority priority) {
        return "priority=" + priority.name();
    }

    @Tool(name = "tag", description = "A parameter type the binder does not convert")
    public String tag(@ToolArg(name = "tags", description = "Tags") List<String> tags) {
        return "tags=" + tags;
    }

    @Prompt(name = "plan_day", description = "Plan my day")
    public String planDay(@PromptArg(name = "hours", description = "Hours available", defaultValue = "8") int hours) {
        return "hours=" + hours;
    }
}
