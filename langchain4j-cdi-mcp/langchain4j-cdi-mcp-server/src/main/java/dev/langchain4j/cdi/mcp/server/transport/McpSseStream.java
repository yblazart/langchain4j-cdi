package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Name binding marking the {@code SseEventSink}-based resource methods of {@link McpEndpoint}, so that
 * {@link McpSseStreamHeadersFilter} adds the stream response headers to them only.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface McpSseStream {}
