package dev.langchain4j.cdi.mcp.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Describes a parameter of an {@link McpTool} method. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface McpToolArg {

    /** Description of the parameter exposed to MCP clients. */
    String value() default "";

    /** Whether this parameter is required. Defaults to true. */
    boolean required() default true;
}
