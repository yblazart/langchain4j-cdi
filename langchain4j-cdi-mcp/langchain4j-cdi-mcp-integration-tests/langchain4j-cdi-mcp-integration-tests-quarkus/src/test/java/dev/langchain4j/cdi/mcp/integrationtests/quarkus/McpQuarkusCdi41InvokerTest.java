package dev.langchain4j.cdi.mcp.integrationtests.quarkus;

import static dev.langchain4j.cdi.mcp.integrationtests.McpTestConstants.ASK_NAME;
import static dev.langchain4j.cdi.mcp.integrationtests.McpTestConstants.CONFIG_APP;
import static dev.langchain4j.cdi.mcp.integrationtests.McpTestConstants.DESCRIBE_SIGNATURE;
import static dev.langchain4j.cdi.mcp.integrationtests.McpTestConstants.GET_WEATHER;
import static dev.langchain4j.cdi.mcp.integrationtests.McpTestConstants.GREET;
import static dev.langchain4j.cdi.mcp.integrationtests.McpTestConstants.MCP_SESSION_ID;
import static dev.langchain4j.cdi.mcp.integrationtests.McpTestConstants.SUMMARIZE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.cdi.mcp.integrationtests.JsonRpcAssertions;
import dev.langchain4j.cdi.mcp.integrationtests.McpHttpResponse;
import dev.langchain4j.cdi.mcp.integrationtests.McpHttpTransport;
import dev.langchain4j.cdi.mcp.integrationtests.McpModernTestRequests;
import dev.langchain4j.cdi.mcp.integrationtests.McpTestRequests;
import dev.langchain4j.cdi.mcp.invoker.cdi41.McpCdi41InvokerProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import java.util.Map;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

/**
 * Proves that ArC actually honours the optional {@code langchain4j-cdi-mcp-invoker-cdi41} module: that it builds CDI
 * 4.1 {@code Invoker}s for the MCP methods of this deployment, and that {@code McpBeanInvoker} resolves every one of
 * them instead of falling back to {@code Method.invoke}.
 *
 * <p>The module degrades to reflection <em>silently</em>, so "the other tests still pass" proves nothing. The assertion
 * therefore reads the provider's own diagnostics. {@code size()} alone is not enough either - a completely broken
 * build-time/runtime key mapping still registers invokers - so the load-bearing assertion is that {@code matchCount()}
 * reaches {@code size()} with {@code missCount() == 0}: every invoker the container built was actually used, and not
 * one MCP method fell back to reflection.
 *
 * <p>The counters are read by injecting the provider straight into the test, which {@code @QuarkusTest} allows because
 * the test shares the JVM and the bean container with the application. That is the least intrusive option available: it
 * needs no test-only endpoint in the shared IT application, no log scraping, and no change to any production class.
 */
@QuarkusTest
class McpQuarkusCdi41InvokerTest {

    private static final Logger LOG = Logger.getLogger(McpQuarkusCdi41InvokerTest.class.getName());

    /**
     * Number of {@code @Tool}/{@code @Prompt}/{@code @Resource} methods in the shared IT application:
     * {@code getWeather}, {@code greet}, {@code describeSignature}, {@code askName}, {@code summarize},
     * {@code getConfig}, {@code getStatus}. This test invokes all seven, so the match count is expected to reach
     * exactly this number.
     */
    private static final int ANNOTATED_MCP_METHODS = 7;

    /** URI of the second resource of {@code ConfigResource}, invoked here only to cover its invoker too. */
    private static final String STATUS_RESOURCE = "data://status";

    @ConfigProperty(name = "quarkus.http.test-port")
    int port;

    /**
     * The synthetic bean registered by the optional module's build-compatible extension. Injection succeeding at all
     * already proves the {@code @Synthesis} phase ran and published the bean under the SPI type.
     */
    @Inject
    McpCdi41InvokerProvider invokerProvider;

    @Test
    void everyMcpMethodIsInvokedThroughAContainerInvokerInsteadOfReflection() {
        assertThat(invokerProvider)
                .as("the CDI 4.1 invoker provider must be a resolvable bean")
                .isNotNull();
        assertThat(invokerProvider.size())
                .as("ArC must have built an invoker for each annotated MCP method")
                .isEqualTo(ANNOTATED_MCP_METHODS);

        // Exercise every MCP method of the deployment - tools, a prompt and both resources - so that the match count
        // can be compared to the number of invokers the container built.
        McpHttpTransport transport = new RestAssuredTransport(port);
        String sessionId = initializeSession(transport);

        JsonRpcAssertions.assertJsonRpcSuccess(
                post(
                        transport,
                        sessionId,
                        McpTestRequests.toolsCallRequest(
                                200, GET_WEATHER, "{\"city\":\"Paris\",\"unit\":\"celsius\"}")),
                200);
        JsonRpcAssertions.assertJsonRpcSuccess(
                post(transport, sessionId, McpTestRequests.toolsCallRequest(201, GREET, "{\"name\":\"Ada\"}")), 201);

        // describeSignature is the only MCP method of the deployment whose parameters are not all java.lang.String:
        // it takes an int, a String[], a List<String> and a nested record. Those are exactly the shapes the build-time
        // language model and the runtime Class could spell differently ("[Ljava.lang.String;", "GreetingTool.Style",
        // an unerased List<String>), and any such disagreement shows up below as a miss.
        JsonObject described = JsonRpcAssertions.assertJsonRpcSuccess(
                post(transport, sessionId, McpTestRequests.toolsCallRequest(207, DESCRIBE_SIGNATURE, "{\"count\":3}")),
                207);
        assertThat(described.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("count=3 tags=none labels=none style=none");

        JsonRpcAssertions.assertJsonRpcSuccess(
                post(
                        transport,
                        sessionId,
                        McpTestRequests.promptsGetRequest(202, SUMMARIZE, "{\"text\":\"Hello world\"}")),
                202);
        JsonRpcAssertions.assertJsonRpcSuccess(
                post(transport, sessionId, McpTestRequests.resourcesReadRequest(203, CONFIG_APP)), 203);
        JsonRpcAssertions.assertJsonRpcSuccess(
                post(transport, sessionId, McpTestRequests.resourcesReadRequest(204, STATUS_RESOURCE)), 204);

        // askName takes an org.mcpjava.server.elicitation.Elicitation framework parameter and signals for input by
        // throwing McpInputRequiredSignal. Running it through a container invoker proves two things reflection used
        // to give for free: framework parameters are still resolved and passed positionally, and the signal reaches
        // McpBeanInvoker unwrapped (Invoker.invoke declares `throws Exception`, not InvocationTargetException).
        JsonObject inputRequired = JsonRpcAssertions.assertJsonRpcSuccess(
                postModern(transport, 205, ASK_NAME, "\"name\":\"askName\",\"arguments\":{}"), 205);
        assertThat(inputRequired.getString("resultType")).isEqualTo("input_required");

        String retry =
                "\"name\":\"askName\",\"arguments\":{},\"requestState\":\"" + inputRequired.getString("requestState")
                        + "\",\"inputResponses\":{\"input-0\":{\"action\":\"accept\",\"content\":{\"name\":\"Ada\"}}}";
        JsonObject completed = JsonRpcAssertions.assertJsonRpcSuccess(postModern(transport, 206, ASK_NAME, retry), 206);
        assertThat(completed.getJsonArray("content").getJsonObject(0).getString("text"))
                .isEqualTo("Hello, Ada!");

        LOG.info(() -> "MCP CDI 4.1 invoker counters on ArC: size=" + invokerProvider.size() + " matches="
                + invokerProvider.matchCount() + " misses=" + invokerProvider.missCount());

        // McpBeanInvoker caches the provider lookup per (bean class, java.lang.reflect.Method) pair, so one match per
        // MCP method is all there is to count however many times a tool is called, and the count is stable whatever
        // order test classes run in.
        assertThat(invokerProvider.matchCount())
                .as("McpBeanInvoker must have resolved a container invoker for every MCP method, not reflected")
                .isEqualTo(ANNOTATED_MCP_METHODS);
        assertThat(invokerProvider.missCount())
                .as("a miss means the build-time key and the runtime key disagree for some method")
                .isZero();
    }

    private static String initializeSession(McpHttpTransport transport) {
        McpHttpResponse response = transport.post("/mcp", McpTestRequests.initializeRequest(1), Map.of());
        JsonRpcAssertions.assertJsonRpcSuccess(response, 1);
        String sessionId = response.header(MCP_SESSION_ID);
        assertThat(sessionId).as(MCP_SESSION_ID + " header").isNotNull().isNotBlank();
        return sessionId;
    }

    private static McpHttpResponse post(McpHttpTransport transport, String sessionId, String body) {
        return transport.post("/mcp", body, Map.of(MCP_SESSION_ID, sessionId));
    }

    /** Posts a sessionless MCP 2026-07-28 {@code tools/call} declaring the elicitation client capability. */
    private static McpHttpResponse postModern(McpHttpTransport transport, Object id, String name, String paramsJson) {
        return transport.post(
                "/mcp",
                McpModernTestRequests.body(id, "tools/call", paramsJson, "{\"elicitation\":{}}"),
                McpModernTestRequests.headers("tools/call", name));
    }
}
