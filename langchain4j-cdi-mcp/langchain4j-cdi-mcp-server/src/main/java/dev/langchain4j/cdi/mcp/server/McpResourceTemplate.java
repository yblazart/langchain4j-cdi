package dev.langchain4j.cdi.mcp.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP resource template provider. Resource templates use URI templates (RFC 6570) to define
 * dynamic resources. The method must accept parameters matching the template variables and return a String.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpResourceTemplate {

    /** The URI template (RFC 6570), e.g. "file:///{path}" or "user://{userId}/profile". */
    String uriTemplate();

    /** Human-readable name of the resource template. */
    String name() default "";

    /** Description of the resource template. */
    String description() default "";

    /** MIME type of the resource content. Defaults to "text/plain". */
    String mimeType() default "text/plain";
}
