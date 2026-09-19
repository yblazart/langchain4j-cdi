package dev.langchain4j.cdi.mcp.integrationtests;

import dev.langchain4j.mcp.client.McpClient;
import org.junit.jupiter.api.Test;

/**
 * Base class for MCP server integration tests. All test logic lives in {@link McpIntegrationTestScenarios}; this class
 * provides {@code @Test}-annotated entry points for frameworks that support test method inheritance (e.g. Quarkus).
 */
@SuppressWarnings("java:S112")
public abstract class AbstractMcpIntegrationTest {

    /** Creates a new instance. */
    public AbstractMcpIntegrationTest() {}

    /**
     * Returns the HTTP transport used to communicate with the MCP endpoint.
     *
     * @return the transport
     */
    protected abstract McpHttpTransport transport();

    private McpIntegrationTestScenarios scenarios() {
        return new McpIntegrationTestScenarios(transport());
    }

    // ---- helpers exposed for subclasses ----

    /**
     * Sends an {@code initialize} request and returns the session id.
     *
     * @return the MCP session id
     */
    protected String initializeSession() {
        return scenarios().initializeSession();
    }

    /**
     * Posts a JSON-RPC body to the MCP endpoint using the given session.
     *
     * @param sessionId the MCP session id
     * @param body the JSON-RPC request body
     * @return the HTTP response
     */
    protected McpHttpResponse postMcp(String sessionId, String body) {
        return scenarios().postMcp(sessionId, body);
    }

    /**
     * Builds a new {@link McpClient} connected to the test MCP endpoint, with automatic era detection.
     *
     * @return a new MCP client
     */
    protected McpClient buildClient() {
        return scenarios().buildClient();
    }

    /**
     * Builds a new {@link McpClient} connected to the test MCP endpoint.
     *
     * @param protocolVersion forced protocol version, or {@code null} for automatic era detection
     * @return a new MCP client
     */
    protected McpClient buildClient(String protocolVersion) {
        return scenarios().buildClient(protocolVersion);
    }

    // ---- Tools via MCP Client ----

    @Test
    void shouldListToolsViaMcpClient() throws Exception {
        scenarios().shouldListToolsViaMcpClient();
    }

    @Test
    void shouldCallToolViaMcpClient() throws Exception {
        scenarios().shouldCallToolViaMcpClient();
    }

    @Test
    void shouldCallToolWithOptionalParamOmitted() throws Exception {
        scenarios().shouldCallToolWithOptionalParamOmitted();
    }

    @Test
    void shouldCallToolWithOptionalParamProvided() throws Exception {
        scenarios().shouldCallToolWithOptionalParamProvided();
    }

    @Test
    void shouldHaveOptionalParameterInToolSchema() throws Exception {
        scenarios().shouldHaveOptionalParameterInToolSchema();
    }

    @Test
    void shouldCallToolWithUnknownNameViaMcpClient() throws Exception {
        scenarios().shouldCallToolWithUnknownNameViaMcpClient();
    }

    @Test
    void shouldListToolsMultipleTimes() throws Exception {
        scenarios().shouldListToolsMultipleTimes();
    }

    @Test
    void shouldDetectModernProtocolAutomatically() throws Exception {
        scenarios().shouldDetectModernProtocolAutomatically();
    }

    @Test
    void shouldCallToolViaLegacyClient() throws Exception {
        scenarios().shouldCallToolViaLegacyClient();
    }

    @Test
    void shouldCallToolViaModernClient() throws Exception {
        scenarios().shouldCallToolViaModernClient();
    }

    @Test
    void shouldListToolsViaLegacyClient() throws Exception {
        scenarios().shouldListToolsViaLegacyClient();
    }

    // ---- Session & HTTP ----

    @Test
    void shouldInitializeSessionSuccessfully() {
        scenarios().shouldInitializeSessionSuccessfully();
    }

    @Test
    void shouldSendInitializedNotification() {
        scenarios().shouldSendInitializedNotification();
    }

    @Test
    void shouldRejectInitializeSentAsNotification() {
        scenarios().shouldRejectInitializeSentAsNotification();
    }

    @Test
    void shouldHandlePing() {
        scenarios().shouldHandlePing();
    }

    @Test
    void shouldDeleteSession() {
        scenarios().shouldDeleteSession();
    }

    @Test
    void shouldReturnCleanJsonForToolCall() {
        scenarios().shouldReturnCleanJsonForToolCall();
    }

    @Test
    void shouldReturnErrorForUnknownMethod() {
        scenarios().shouldReturnErrorForUnknownMethod();
    }

    @Test
    void shouldReturnParseErrorForMalformedBody() {
        scenarios().shouldReturnParseErrorForMalformedBody();
    }

    // ---- Resources ----

    @Test
    void shouldListResources() {
        scenarios().shouldListResources();
    }

    @Test
    void shouldReadResource() {
        scenarios().shouldReadResource();
    }

    @Test
    void shouldReturnErrorForUnknownResource() {
        scenarios().shouldReturnErrorForUnknownResource();
    }

    // ---- Prompts ----

    @Test
    void shouldListPrompts() {
        scenarios().shouldListPrompts();
    }

    @Test
    void shouldGetPrompt() {
        scenarios().shouldGetPrompt();
    }

    @Test
    void shouldReturnErrorForUnknownPrompt() {
        scenarios().shouldReturnErrorForUnknownPrompt();
    }

    // ---- Logging ----

    @Test
    void shouldSetLogLevel() {
        scenarios().shouldSetLogLevel();
    }

    @Test
    void shouldReturnErrorForInvalidLogLevel() {
        scenarios().shouldReturnErrorForInvalidLogLevel();
    }

    // ---- Resource Subscriptions ----

    @Test
    void shouldSubscribeToResource() {
        scenarios().shouldSubscribeToResource();
    }

    @Test
    void shouldUnsubscribeFromResource() {
        scenarios().shouldUnsubscribeFromResource();
    }

    // ---- Resource Templates ----

    @Test
    void shouldListResourceTemplates() {
        scenarios().shouldListResourceTemplates();
    }

    // ---- Completion ----

    @Test
    void shouldReturnEmptyCompletionForUnknownRef() {
        scenarios().shouldReturnEmptyCompletionForUnknownRef();
    }

    // ---- Notifications ----

    @Test
    void shouldAcknowledgeCancellation() {
        scenarios().shouldAcknowledgeCancellation();
    }

    @Test
    void shouldAcceptClientJsonRpcResponse() {
        scenarios().shouldAcceptClientJsonRpcResponse();
    }

    @Test
    void shouldAcceptAnyNotificationShapedMessage() {
        scenarios().shouldAcceptAnyNotificationShapedMessage();
    }

    // ---- Capabilities ----

    @Test
    void shouldDeclareAllCapabilities() {
        scenarios().shouldDeclareAllCapabilities();
    }

    // ---- Negative session tests ----

    @Test
    void shouldRejectRequestWithInvalidSessionId() {
        scenarios().shouldRejectRequestWithInvalidSessionId();
    }

    @Test
    void shouldRejectForeignOrigin() {
        scenarios().shouldRejectForeignOrigin();
    }

    @Test
    void shouldAcceptLocalhostOrigin() {
        scenarios().shouldAcceptLocalhostOrigin();
    }

    // ---- Modern protocol (2026-07-28) ----

    @Test
    void shouldDiscoverSupportedVersions() {
        scenarios().shouldDiscoverSupportedVersions();
    }

    @Test
    void shouldServeModernRequestsWithoutSession() {
        scenarios().shouldServeModernRequestsWithoutSession();
    }

    @Test
    void shouldPublishIconsInTheModernEraOnly() {
        scenarios().shouldPublishIconsInTheModernEraOnly();
    }

    @Test
    void shouldCallToolWithModernRequest() {
        scenarios().shouldCallToolWithModernRequest();
    }

    @Test
    void shouldRejectMismatchedNameHeader() {
        scenarios().shouldRejectMismatchedNameHeader();
    }

    // ---- SEP-2243 ----

    @Test
    void shouldAcceptMirroredParamHeadersMatchingTheBody() {
        scenarios().shouldAcceptMirroredParamHeadersMatchingTheBody();
    }

    @Test
    void shouldMatchParamHeaderNamesCaseInsensitively() {
        scenarios().shouldMatchParamHeaderNamesCaseInsensitively();
    }

    @Test
    void shouldAcceptABase64WrappedParamHeader() {
        scenarios().shouldAcceptABase64WrappedParamHeader();
    }

    @Test
    void shouldRejectAParamHeaderDisagreeingWithTheBody() {
        scenarios().shouldRejectAParamHeaderDisagreeingWithTheBody();
    }

    @Test
    void shouldRejectAnOmittedParamHeaderWhenTheBodyCarriesTheValue() {
        scenarios().shouldRejectAnOmittedParamHeaderWhenTheBodyCarriesTheValue();
    }

    @Test
    void shouldRejectAnIntegerParamHeaderThatIsNotTheDecimalBodyValue() {
        scenarios().shouldRejectAnIntegerParamHeaderThatIsNotTheDecimalBodyValue();
    }

    @Test
    void shouldRejectMalformedBase64ParamHeaderWith400NotWith500() {
        scenarios().shouldRejectMalformedBase64ParamHeaderWith400NotWith500();
    }

    @Test
    void shouldIgnoreParamHeadersInTheLegacyEra() {
        scenarios().shouldIgnoreParamHeadersInTheLegacyEra();
    }

    // ---- Argument binding (langchain4j-cdi#298) ----

    @Test
    void shouldAdvertiseDefaultedArgumentsAsNotRequired() {
        scenarios().shouldAdvertiseDefaultedArgumentsAsNotRequired();
    }

    @Test
    void shouldApplyArgumentDefaultsInBothEras() {
        scenarios().shouldApplyArgumentDefaultsInBothEras();
    }

    @Test
    void shouldBindEnumArgumentInBothEras() {
        scenarios().shouldBindEnumArgumentInBothEras();
    }

    @Test
    void shouldRejectWronglyTypedToolArgumentAsInvalidParamsInLegacyEra() {
        scenarios().shouldRejectWronglyTypedToolArgumentAsInvalidParamsInLegacyEra();
    }

    @Test
    void shouldReportWronglyTypedToolArgumentAsToolErrorInModernEra() {
        scenarios().shouldReportWronglyTypedToolArgumentAsToolErrorInModernEra();
    }

    @Test
    void shouldRejectWronglyTypedPromptArgumentAsInvalidParamsInBothEras() {
        scenarios().shouldRejectWronglyTypedPromptArgumentAsInvalidParamsInBothEras();
    }

    @Test
    void shouldRejectUnsupportedProtocolVersion() {
        scenarios().shouldRejectUnsupportedProtocolVersion();
    }

    @Test
    void shouldReturn404ForLegacyOnlyMethodInModernEra() {
        scenarios().shouldReturn404ForLegacyOnlyMethodInModernEra();
    }

    @Test
    void shouldAcceptModernNotificationWith202() {
        scenarios().shouldAcceptModernNotificationWith202();
    }

    @Test
    void shouldRoundTripElicitationWithMrtr() {
        scenarios().shouldRoundTripElicitationWithMrtr();
    }

    @Test
    void shouldRequireDeclaredElicitationCapability() {
        scenarios().shouldRequireDeclaredElicitationCapability();
    }

    @Test
    void shouldAcknowledgeListenSubscription() throws Exception {
        scenarios().shouldAcknowledgeListenSubscription();
    }

    @Test
    void shouldRejectListenWithMismatchedProtocolVersionHeader() {
        scenarios().shouldRejectListenWithMismatchedProtocolVersionHeader();
    }
}
