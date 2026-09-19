/**
 * MCP (Model Context Protocol) server runtime for LangChain4j CDI: exposes {@code @Tool}/{@code @Prompt}/
 * {@code @Resource} CDI beans over JSON-RPC 2.0 / Streamable HTTP at the {@code /mcp} endpoint.
 *
 * <p>Declared as an {@code open module} so the CDI container and JAX-RS runtime can reflectively access the endpoint,
 * registries and transport handlers. Being {@code open} only grants <em>reflective</em> access, so the packages an
 * application compiles against have to be exported explicitly as well — see the {@code exports} below.
 */
open module dev.langchain4j.cdi.mcp.server {
    requires transitive jakarta.cdi;
    requires transitive mcp.server.api;
    requires jakarta.inject;
    requires jakarta.json;
    requires jakarta.ws.rs;
    requires jakarta.annotation;
    requires jakarta.json.bind;
    requires java.logging;

    // The MCP framework types (McpLog, McpConnection, Roots, Sampling, Elicitation and their request/response
    // types) that a @Tool/@Prompt/@Resource method may declare as parameters. Documented in
    // langchain4j-cdi-mcp/README.md, "Framework Types".
    exports dev.langchain4j.cdi.mcp.server.api;

    // Wire-format records that appear in the signatures of the exported `api` types: McpRoot (Roots.listAndAwait),
    // McpSamplingMessage and McpModelPreferences (SamplingRequest.Builder). Without this export those documented
    // methods are unusable, since a consumer cannot name their parameter/return types.
    exports dev.langchain4j.cdi.mcp.server.protocol;

    // Registries of discovered tools/prompts/resources, consumed by the MCP discovery extensions.
    exports dev.langchain4j.cdi.mcp.server.registry;

    // McpServerConfig: the @Named("mcp-server") bean an application produces to advertise its own MCP server name
    // and version. JPMS cannot export a single type, and relocating McpServerConfig would break the project's
    // add/deprecate-never-remove rule, so the whole package is exported. The rest of this package (McpEndpoint, the
    // session/notification/request managers, McpExceptionMapper) is server-internal plumbing: it is not documented
    // as an extension point and carries no compatibility promise.
    exports dev.langchain4j.cdi.mcp.server.transport;

    // The MCP framework's McpServerSPILoader finds its implementation through ServiceLoader.
    // META-INF/services/org.mcpjava.server.spi.McpServerSPI registers it for the class path, but the JDK ignores
    // that file for a named module: without this clause, every call that goes through McpServerSPILoader fails with
    // "No McpServerSPI implementation found" whenever this jar is on the module path (a jlink image, a Vidocq boot
    // layer). ModuleDescriptorServicesTest keeps the two declarations identical.
    provides org.mcpjava.server.spi.McpServerSPI with
            dev.langchain4j.cdi.mcp.server.spi.CdiMcpServerSPI;
}
