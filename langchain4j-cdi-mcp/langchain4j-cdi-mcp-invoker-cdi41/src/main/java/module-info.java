/**
 * Optional add-on that makes the MCP server invoke {@code @Tool}/{@code @Prompt}/{@code @Resource} bean methods through
 * the CDI 4.1 {@code jakarta.enterprise.invoke} API instead of reflection.
 *
 * <p>Only useful on a Jakarta EE 11 / CDI 4.1 runtime that supports build-compatible extensions (Quarkus, Helidon,
 * Vidocq/Vauban). The core MCP modules stay on Jakarta EE 10 / CDI 4.0.1 and keep the reflective path; adding this
 * artifact to an application is the whole opt-in.
 *
 * <p>Declared as an {@code open module} so the CDI container can instantiate the extension, the synthetic bean creation
 * function and the provider itself.
 */
open module dev.langchain4j.cdi.mcp.invoker.cdi41 {
    requires jakarta.cdi;
    requires jakarta.cdi.lang.model;
    requires dev.langchain4j.cdi.mcp.server;
    requires mcp.server.api;
    requires java.logging;

    exports dev.langchain4j.cdi.mcp.invoker.cdi41;

    provides jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension with
            dev.langchain4j.cdi.mcp.invoker.cdi41.McpInvokerBuildCompatibleExtension;
}
