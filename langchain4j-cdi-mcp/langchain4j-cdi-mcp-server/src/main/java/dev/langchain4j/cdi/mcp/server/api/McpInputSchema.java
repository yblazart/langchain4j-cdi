package dev.langchain4j.cdi.mcp.server.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Supplies a hand-written JSON Schema 2020-12 document as a {@code @Tool} method's {@code inputSchema}, verbatim,
 * instead of the one {@code dev.langchain4j.cdi.mcp.server.schema.JsonSchemaGenerator} derives from the Java signature.
 *
 * <p>The reflection-based generator can only ever produce a flat {@code properties}/{@code required} object: it has no
 * way to emit composition ({@code oneOf}, {@code anyOf}, {@code allOf}, {@code not}), conditional
 * ({@code if}/{@code then}/{@code else}), or reference ({@code $ref}, {@code $defs}, {@code $anchor}) keywords, because
 * none of those has a Java-signature counterpart. This annotation is the escape hatch for a tool whose input genuinely
 * needs one of those constructs.
 *
 * <p>The supplied document is served verbatim, identically, to both protocol eras this server supports: the MCP
 * {@code 2026-07-28} schema explicitly permits the full JSON Schema 2020-12 vocabulary in {@code inputSchema} beyond
 * the required root {@code type: "object"} (SEP-2106), and the {@code 2025-03-26} legacy {@code Tool.inputSchema}
 * definition already allows {@code additionalProperties: true} on its {@code properties} entries, so nothing here needs
 * an era-specific variant. A tool that does not use this annotation is completely unaffected, in both eras, byte for
 * byte.
 *
 * <h2>Constraints enforced at registration time</h2>
 *
 * <ol>
 *   <li>the value must parse as a JSON object whose root carries {@code "type": "object"} — tool arguments are always a
 *       JSON object, so anything else can never be a legal {@code inputSchema};
 *   <li>every property named in a top-level {@code required} array must have a same-named, bindable Java parameter —
 *       arguments are still bound to parameters by name at call time, so an advertised required argument that can never
 *       bind would be a silent failure for the client, not a registration-time one;
 *   <li>this annotation must not be combined with {@link McpHeader} on the same method. SEP-2243 request validation
 *       reads its header designations from the reflected {@code @McpHeader} annotations, independently of whatever the
 *       hand-written schema says; a hand-written schema that a tool author forgets to keep in sync with those
 *       designations — or one that predates a later {@code @McpHeader} addition — would validate {@code Mcp-Param-*}
 *       headers the client was never told to send, because the client only ever sees the {@code x-mcp-header} keyword
 *       the *schema* carries. Rejecting the combination outright removes that entire class of drift rather than trying
 *       to keep two independently-authored sources of truth consistent.
 * </ol>
 *
 * <p>A violation fails the deployment, naming the tool and the violated rule, the same way {@link McpHeader} violations
 * do.
 *
 * <p><strong>Provisional API.</strong> {@code org.mcpjava:mcp-server-api} has no hook for a hand-written input schema —
 * {@link org.mcpjava.server.tools.Tool} generates {@code inputSchema} implicitly from the method signature and offers
 * no override. This annotation is intentionally the smallest useful surface: a literal JSON Schema 2020-12 document as
 * a string, parsed once at registration. It exists here, outside {@code org.mcpjava}, the same way {@link McpHeader}
 * does, and is intended to migrate to (or be superseded by) an upstream mechanism should that project adopt one — see
 * quarkus-mcp-server's {@code @Tool.InputSchema(generator = ...)}, a heavier, pluggable-bean design considered and not
 * followed here because a literal document is sufficient for every case this module needs to support today.
 *
 * <pre>{@code
 * @Tool(name = "example")
 * @McpInputSchema("{\"type\":\"object\",\"properties\":{...}}")
 * ToolResponse example(String arg) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpInputSchema {

    /**
     * The hand-written JSON Schema 2020-12 document, as literal JSON text, served verbatim as the tool's
     * {@code inputSchema} in both protocol eras.
     *
     * @return the raw JSON Schema text
     */
    String value();
}
