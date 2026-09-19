package dev.langchain4j.cdi.mcp.integrationtests.helidon;

import dev.langchain4j.cdi.mcp.integrationtests.McpIntegrationTestScenarios;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

/**
 * Helidon integration tests. Helidon's {@code @HelidonTest} does not support test method inheritance, so this class
 * delegates to {@link McpIntegrationTestScenarios}.
 */
@SuppressWarnings("java:S5786")
@HelidonTest
public class McpHelidonIntegrationTest {

    @Inject
    WebTarget injectedTarget;

    private McpIntegrationTestScenarios scenarios() {
        return new McpIntegrationTestScenarios(new HelidonWebTargetTransport(injectedTarget));
    }

    // --- Tools via MCP Client ---

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

    // --- Session & HTTP ---

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

    // --- Resources ---

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

    // --- Prompts ---

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

    // --- Logging ---

    @Test
    void shouldSetLogLevel() {
        scenarios().shouldSetLogLevel();
    }

    @Test
    void shouldReturnErrorForInvalidLogLevel() {
        scenarios().shouldReturnErrorForInvalidLogLevel();
    }

    // --- Resource Subscriptions ---

    @Test
    void shouldSubscribeToResource() {
        scenarios().shouldSubscribeToResource();
    }

    @Test
    void shouldUnsubscribeFromResource() {
        scenarios().shouldUnsubscribeFromResource();
    }

    // --- Resource Templates ---

    @Test
    void shouldListResourceTemplates() {
        scenarios().shouldListResourceTemplates();
    }

    // --- Completion ---

    @Test
    void shouldReturnEmptyCompletionForUnknownRef() {
        scenarios().shouldReturnEmptyCompletionForUnknownRef();
    }

    // --- Notifications ---

    @Test
    void shouldAcknowledgeCancellation() {
        scenarios().shouldAcknowledgeCancellation();
    }

    @Test
    void shouldAcceptRootsListChangedNotification() {
        scenarios().shouldAcceptRootsListChangedNotification();
    }

    @Test
    void shouldAcceptClientJsonRpcResponse() {
        scenarios().shouldAcceptClientJsonRpcResponse();
    }

    @Test
    void shouldAcceptAnyNotificationShapedMessage() {
        scenarios().shouldAcceptAnyNotificationShapedMessage();
    }

    // --- Capabilities ---

    @Test
    void shouldDeclareAllCapabilities() {
        scenarios().shouldDeclareAllCapabilities();
    }

    // --- Negative session tests ---

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

    // --- Modern protocol (2026-07-28) ---

    @Test
    void shouldDiscoverSupportedVersions() {
        scenarios().shouldDiscoverSupportedVersions();
    }

    @Test
    void shouldServeModernRequestsWithoutSession() {
        scenarios().shouldServeModernRequestsWithoutSession();
    }

    @Test
    void shouldCallToolWithModernRequest() {
        scenarios().shouldCallToolWithModernRequest();
    }

    @Test
    void shouldRejectMismatchedNameHeader() {
        scenarios().shouldRejectMismatchedNameHeader();
    }

    // --- SEP-2243 ---

    @Test
    void shouldAcceptMirroredParamHeadersMatchingTheBody() {
        scenarios().shouldAcceptMirroredParamHeadersMatchingTheBody();
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
