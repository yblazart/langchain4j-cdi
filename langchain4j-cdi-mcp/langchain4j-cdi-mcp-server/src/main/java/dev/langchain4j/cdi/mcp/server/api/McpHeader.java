package dev.langchain4j.cdi.mcp.server.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates a tool argument whose value a conforming client must mirror into an HTTP request header, as specified by
 * <a href="https://modelcontextprotocol.io/specification/draft/server/tools#custom-headers">SEP-2243</a>.
 *
 * <p>The designation is published in the tool's generated JSON Schema as the {@code x-mcp-header} keyword on the
 * argument's property schema. A client that invokes the tool sends the argument value in the {@code Mcp-Param-<value>}
 * header in addition to the JSON-RPC request body. Only the MCP {@code 2026-07-28} era carries the keyword; the
 * {@code 2025-03-26} legacy era, which predates SEP-2243, emits the schema unchanged.
 *
 * <p>Applied next to {@link org.mcpjava.server.tools.ToolArg}:
 *
 * <pre>{@code
 * @Tool(name = "example")
 * String example(@ToolArg(name = "tenant") @McpHeader("X-Tenant-Id") String tenant) { ... }
 * }</pre>
 *
 * <h2>Constraints</h2>
 *
 * SEP-2243 puts four constraints on the value, all enforced at registration time by
 * {@code dev.langchain4j.cdi.mcp.server.schema.McpHeaderValidator}:
 *
 * <ol>
 *   <li>it must not be empty;
 *   <li>it must contain only ASCII characters, excluding space and {@code :};
 *   <li>it must be case-insensitively unique within a single tool definition;
 *   <li>it may only be applied to a parameter whose JSON Schema type is {@code integer}, {@code string} or
 *       {@code boolean} — {@code number} is explicitly <em>not</em> permitted, so {@code float}, {@code double} and
 *       {@link java.math.BigDecimal} parameters are rejected.
 * </ol>
 *
 * <p>Violations fail the deployment rather than the call. SEP-2243 requires a client to <em>exclude</em> a tool whose
 * designation is malformed from the result of {@code tools/list}, so an invalid value would otherwise make the tool
 * silently disappear from the client's catalogue with no error anywhere.
 *
 * <p>SEP-2243 also advises server authors not to designate sensitive parameters (passwords, API keys, tokens, PII): the
 * value travels in a header, where intermediaries can read and log it.
 *
 * <p><strong>Provisional API.</strong> This is the first user-facing annotation this module defines outside
 * {@code org.mcpjava}. It exists because {@code org.mcpjava:mcp-server-api} has no hook for the designation —
 * {@link org.mcpjava.server.tools.ToolArg} exposes only {@code name}, {@code description}, {@code required} and
 * {@code defaultValue}. It is intended to migrate to that project should it adopt SEP-2243, at which point this
 * annotation would be deprecated in favour of the upstream one.
 *
 * <p>The name follows the WildFly MCP implementation, which independently arrived at {@code @Header} for this same
 * SEP-2243 keyword. Agreeing on the noun now — while nothing is released — costs nothing and makes a later convergence
 * on an {@code org.mcpjava} annotation cheaper. See <a
 * href="https://github.com/mcp-java/java-mcp-annotations/issues/70">mcp-java/java-mcp-annotations#70</a>.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpHeader {

    /**
     * The header name suffix carried in the {@code x-mcp-header} schema keyword; a client mirrors the argument value
     * into the {@code Mcp-Param-<value>} request header.
     *
     * @return the header name, non-empty, ASCII, without space or {@code :}
     */
    String value();
}
