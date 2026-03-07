package dev.langchain4j.cdi.mcp.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Describes a parameter of an {@link McpPrompt} method. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface McpPromptArg {

    /** Description of the argument. */
    String value() default "";

    /** Whether this argument is required. Defaults to true. */
    boolean required() default true;
}
