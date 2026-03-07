package dev.langchain4j.cdi.mcp.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as an MCP resource provider. The method must return a String (text content). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpResource {

    /** The resource URI (e.g. "file:///config.json" or "data://users"). */
    String uri();

    /** Human-readable name of the resource. */
    String name() default "";

    /** Description of the resource. */
    String description() default "";

    /** MIME type of the resource content. Defaults to "text/plain". */
    String mimeType() default "text/plain";
}
