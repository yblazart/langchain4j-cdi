package dev.langchain4j.cdi.mcp.modulepathconsumer;

import dev.langchain4j.cdi.mcp.server.api.Elicitation;
import dev.langchain4j.cdi.mcp.server.api.ElicitationResponse;
import dev.langchain4j.cdi.mcp.server.api.McpConnection;
import dev.langchain4j.cdi.mcp.server.api.McpInteractionResults;
import dev.langchain4j.cdi.mcp.server.api.McpInteractions;
import dev.langchain4j.cdi.mcp.server.api.McpLog;
import dev.langchain4j.cdi.mcp.server.api.Roots;
import dev.langchain4j.cdi.mcp.server.api.Sampling;
import dev.langchain4j.cdi.mcp.server.api.SamplingResponse;
import dev.langchain4j.cdi.mcp.server.protocol.McpRoot;
import dev.langchain4j.cdi.mcp.server.protocol.McpSamplingMessage;
import dev.langchain4j.cdi.mcp.server.transport.McpServerConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import java.util.List;
import org.mcpjava.server.Cancellation;
import org.mcpjava.server.progress.Progress;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/**
 * Compile-time proof that the MCP server module exports everything {@code langchain4j-cdi-mcp/README.md} documents as
 * user-facing. Every reference below mirrors a documented usage:
 *
 * <ul>
 *   <li>the "Framework Types" table — {@code McpLog}, {@code Progress}, {@code Cancellation}, {@code McpConnection},
 *       {@code Roots}, {@code Sampling}, {@code Elicitation} as {@code @Tool} method parameters;
 *   <li>the types those framework interfaces return or accept in their own signatures ({@code McpRoot},
 *       {@code McpSamplingMessage}, {@code SamplingResponse}, {@code ElicitationResponse});
 *   <li>the MRTR server-chosen input keys (SEP-2322): {@code ElicitationRequest.Builder.setKey},
 *       {@code SamplingRequest.Builder.setKey}, {@code Roots.listAndAwait(String)};
 *   <li>the MRTR batch interactions (SEP-2322): {@code McpInteractions} injected as a method parameter,
 *       {@code McpInteractions.Batch} and {@code McpInteractionResults};
 *   <li>the {@code @Named("mcp-server")} {@link McpServerConfig} producer an application declares to advertise its own
 *       server name and version.
 * </ul>
 *
 * <p>Nothing here is executed. It only has to <em>compile on the module path</em>; a missing {@code exports} in the MCP
 * server descriptor makes {@code javac} fail with "package … is not visible".
 */
@ApplicationScoped
public class DocumentedMcpApiConsumer {

    /** Producer an application declares to override the advertised MCP server identity. */
    @Produces
    @ApplicationScoped
    @Named("mcp-server")
    public McpServerConfig serverConfig() {
        return McpServerConfig.builder()
                .serverName("module-path-consumer")
                .serverVersion("1.0.0")
                .build();
    }

    /** Uses every framework type the README lists as injectable into a tool method. */
    @Tool(description = "Exercises the documented MCP framework types")
    public String documentedFrameworkTypes(
            @ToolArg(description = "Anything") String input,
            McpLog log,
            Progress progress,
            Cancellation cancellation,
            McpConnection connection,
            Roots roots,
            Sampling sampling,
            Elicitation elicitation,
            McpInteractions interactions) {

        log.info("connection %s is %s", connection.id(), connection.status());
        if (log.level() == McpLog.LogLevel.DEBUG) {
            log.debug("progress token available: %s", progress != null);
        }
        if (cancellation.check().isRequested()) {
            return "cancelled";
        }

        List<McpRoot> clientRoots =
                roots.isSupported() ? roots.listAndAwait("workspace-roots") : List.of();

        if (sampling.isSupported()) {
            SamplingResponse response = sampling.requestBuilder()
                    .addMessage(new McpSamplingMessage("user", input))
                    .setMaxTokens(16L)
                    .setKey("summary")
                    .build()
                    .sendAndAwait();
            return response.model() + " saw " + clientRoots.size() + " roots";
        }

        if (elicitation.isSupported()) {
            ElicitationResponse response = elicitation
                    .requestBuilder()
                    .setMessage("Confirm?")
                    .setKey("user_name")
                    .build()
                    .sendAndAwait();
            return String.valueOf(response.action());
        }

        if (elicitation.isSupported() && sampling.isSupported() && roots.isSupported()) {
            McpInteractionResults answers = interactions
                    .batch()
                    .elicit(
                            "user_name",
                            elicitation.requestBuilder().setMessage("Your name?").build())
                    .sample(
                            "summary",
                            sampling.requestBuilder()
                                    .addMessage(new McpSamplingMessage("user", input))
                                    .setMaxTokens(16L)
                                    .build())
                    .roots("roots")
                    .awaitAll();
            return answers.elicitation("user_name").content().getString("name") + " / "
                    + answers.sampling("summary").model() + " / "
                    + answers.roots("roots").size();
        }

        return input;
    }
}
