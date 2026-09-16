package dev.langchain4j.cdi.mcp.server.fixtures;

import org.mcpjava.server.tools.Tool;

/**
 * Two bean classes sharing one annotated method inherited from a common base.
 *
 * <p>The registries collect MCP methods with {@code beanClass.getMethods()}, which returns the <em>declaring</em>
 * {@link java.lang.reflect.Method} for an inherited method. {@code FirstTool} and {@code SecondTool} therefore yield
 * {@link java.lang.reflect.Method} objects that are {@code equals()} to each other, even though they belong to two
 * different beans - exactly the situation in which an invoker cache keyed on the method alone would hand the second
 * bean the invoker built for the first.
 */
public final class InheritedTools {

    private InheritedTools() {}

    /** Base class declaring the shared {@code @Tool} method. */
    public abstract static class BaseTool {

        /** Creates a new instance. */
        protected BaseTool() {}

        /**
         * Describes the bean; inherited unchanged by both subclasses.
         *
         * @return the description
         */
        @Tool(description = "Describe the bean")
        public String describe() {
            return getClass().getSimpleName();
        }
    }

    /** First bean inheriting {@link BaseTool#describe()}. */
    public static class FirstTool extends BaseTool {

        /** Creates a new instance. */
        public FirstTool() {}
    }

    /** Second bean inheriting the very same {@link BaseTool#describe()}. */
    public static class SecondTool extends BaseTool {

        /** Creates a new instance. */
        public SecondTool() {}
    }
}
