package dev.langchain4j.cdi.mcp.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as an MCP tool that can be discovered and invoked by MCP clients. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpTool {

    /** The tool name. Defaults to the method name if empty. */
    String name() default "";

    /** The tool description exposed to MCP clients. */
    String value() default "";
}
