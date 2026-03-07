package dev.langchain4j.cdi.mcp.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP prompt template. The method must return a String (the prompt text) or a List of
 * McpPromptMessage.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpPrompt {

    /** The prompt name. Defaults to the method name if empty. */
    String name() default "";

    /** Description of the prompt. */
    String value() default "";
}
