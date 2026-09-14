package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.api.McpRequestContext;
import dev.langchain4j.cdi.mcp.server.error.McpErrorCode;
import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.error.McpToolNotFoundException;
import dev.langchain4j.cdi.mcp.server.protocol.McpCursor;
import dev.langchain4j.cdi.mcp.server.protocol.McpImplementation;
import dev.langchain4j.cdi.mcp.server.protocol.McpJsonSerializer;
import dev.langchain4j.cdi.mcp.server.protocol.McpListPromptsResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpListResourceTemplatesResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpListResourcesResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpListToolsResult;
import dev.langchain4j.cdi.mcp.server.protocol.McpPagination;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptArgument;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptMessage;
import dev.langchain4j.cdi.mcp.server.protocol.McpPromptModel;
import dev.langchain4j.cdi.mcp.server.protocol.McpResourceModel;
import dev.langchain4j.cdi.mcp.server.protocol.McpResourceTemplateModel;
import dev.langchain4j.cdi.mcp.server.protocol.McpServerCapabilities;
import dev.langchain4j.cdi.mcp.server.registry.McpBeanInvoker;
import dev.langchain4j.cdi.mcp.server.registry.McpPromptDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpPromptRegistry;
import dev.langchain4j.cdi.mcp.server.registry.McpResourceDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpResourceRegistry;
import dev.langchain4j.cdi.mcp.server.registry.McpResourceTemplateDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpToolDescriptor;
import dev.langchain4j.cdi.mcp.server.registry.McpToolInvoker;
import dev.langchain4j.cdi.mcp.server.registry.McpToolRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import org.mcpjava.server.prompts.PromptMessage;
import org.mcpjava.server.prompts.PromptResponse;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.tools.ToolResponse;

/**
 * Business logic shared by every protocol era: tool/resource/prompt listing and invocation, and completion. This
 * service is transport-agnostic - it never touches {@code jakarta.ws.rs.core.Response} and never validates session
 * state (that remains the caller's responsibility).
 */
@ApplicationScoped
public class McpFeatureService {

    private McpToolRegistry toolRegistry;
    private McpResourceRegistry resourceRegistry;
    private McpPromptRegistry promptRegistry;
    private McpToolInvoker toolInvoker;
    private McpBeanInvoker beanInvoker;
    private McpCancellationManager cancellationManager;
    private McpServerConfigResolver config;

    /** No-arg constructor required by CDI proxying. */
    public McpFeatureService() {}

    /**
     * CDI injection constructor.
     *
     * @param toolRegistry registry of available MCP tools
     * @param resourceRegistry registry of available MCP resources
     * @param promptRegistry registry of available MCP prompts
     * @param toolInvoker invokes tool methods on CDI beans
     * @param beanInvoker invokes prompt and resource methods on CDI beans
     * @param cancellationManager tracks cancellation state per request
     * @param config resolves the server configuration
     */
    @Inject
    public McpFeatureService(
            McpToolRegistry toolRegistry,
            McpResourceRegistry resourceRegistry,
            McpPromptRegistry promptRegistry,
            McpToolInvoker toolInvoker,
            McpBeanInvoker beanInvoker,
            McpCancellationManager cancellationManager,
            McpServerConfigResolver config) {
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.promptRegistry = promptRegistry;
        this.toolInvoker = toolInvoker;
        this.beanInvoker = beanInvoker;
        this.cancellationManager = cancellationManager;
        this.config = config;
    }

    /**
     * Returns the capabilities advertised by this server during initialization.
     *
     * @return the server capabilities
     */
    public McpServerCapabilities capabilities() {
        return new McpServerCapabilities(
                new McpServerCapabilities.ToolsCapability(true),
                new McpServerCapabilities.ResourcesCapability(true, true),
                new McpServerCapabilities.PromptsCapability(true),
                McpServerCapabilities.LoggingCapability.INSTANCE,
                McpServerCapabilities.CompletionsCapability.INSTANCE);
    }

    /**
     * Returns the server implementation info advertised during initialization.
     *
     * @return the server implementation info
     */
    public McpImplementation serverInfo() {
        McpServerConfig c = config.get();
        return new McpImplementation(c.getServerName(), c.getServerVersion());
    }

    /**
     * Extracts the pagination cursor from a request's parameters.
     *
     * @param params the request parameters, may be {@code null}
     * @return the cursor, or {@code null} if absent
     */
    public static String cursor(JsonObject params) {
        return params != null ? params.getString("cursor", null) : null;
    }

    /**
     * Lists the registered tools.
     *
     * @param cursor the pagination cursor, or {@code null} for the first page
     * @return the {@code tools/list} result as a JSON object
     */
    public JsonObject listTools(String cursor) {
        McpPagination.Page<McpToolDescriptor> page =
                McpPagination.paginate(new ArrayList<>(toolRegistry.listTools()), cursor);
        return McpJsonSerializer.toJsonObject(new McpListToolsResult(
                page.items().stream().map(McpToolDescriptor::toWireFormat).toList(), nextCursor(page)));
    }

    /**
     * Lists the registered resources.
     *
     * @param cursor the pagination cursor, or {@code null} for the first page
     * @return the {@code resources/list} result as a JSON object
     */
    public JsonObject listResources(String cursor) {
        McpPagination.Page<McpResourceDescriptor> page =
                McpPagination.paginate(new ArrayList<>(resourceRegistry.listResources()), cursor);
        McpListResourcesResult result = new McpListResourcesResult(
                page.items().stream()
                        .map(r -> McpResourceModel.of(r.getUri(), r.getName(), r.getDescription(), r.getMimeType()))
                        .toList(),
                nextCursor(page));
        return McpJsonSerializer.toJsonObject(result);
    }

    /**
     * Lists the registered resource templates.
     *
     * @param cursor the pagination cursor, or {@code null} for the first page
     * @return the {@code resources/templates/list} result as a JSON object
     */
    public JsonObject listResourceTemplates(String cursor) {
        McpPagination.Page<McpResourceTemplateDescriptor> page =
                McpPagination.paginate(new ArrayList<>(resourceRegistry.listTemplates()), cursor);
        McpListResourceTemplatesResult result = new McpListResourceTemplatesResult(
                page.items().stream()
                        .map(t -> McpResourceTemplateModel.of(
                                t.getUriTemplate(), t.getName(), t.getDescription(), t.getMimeType()))
                        .toList(),
                nextCursor(page));
        return McpJsonSerializer.toJsonObject(result);
    }

    /**
     * Lists the registered prompts.
     *
     * @param cursor the pagination cursor, or {@code null} for the first page
     * @return the {@code prompts/list} result as a JSON object
     */
    public JsonObject listPrompts(String cursor) {
        McpPagination.Page<McpPromptDescriptor> page =
                McpPagination.paginate(new ArrayList<>(promptRegistry.listPrompts()), cursor);
        McpListPromptsResult result = new McpListPromptsResult(
                page.items().stream()
                        .map(p -> McpPromptModel.of(
                                p.getName(),
                                p.getDescription(),
                                p.getArguments().stream()
                                        .map(a -> new McpPromptArgument(a.name(), a.description(), a.required()))
                                        .toList()))
                        .toList(),
                nextCursor(page));
        return McpJsonSerializer.toJsonObject(result);
    }

    /**
     * Invokes a tool by name.
     *
     * @param requestId the JSON-RPC request id
     * @param params the {@code tools/call} parameters ({@code name}, {@code arguments})
     * @param ctx the request context (progress, cancellation, client requester, response channel)
     * @param session the MCP session, or {@code null}
     * @return the {@code tools/call} result as a JSON object
     */
    public JsonObject callTool(Object requestId, JsonObject params, McpRequestContext ctx, McpSession session) {
        String toolName = params != null ? params.getString("name", null) : null;
        if (toolName == null) {
            throw new McpException(requestId, McpErrorCode.INVALID_PARAMS, "Missing tool name");
        }
        JsonObject arguments = params.get("arguments") instanceof JsonObject a ? a : null;
        McpToolDescriptor tool =
                toolRegistry.findTool(toolName).orElseThrow(() -> new McpToolNotFoundException(requestId, toolName));
        cancellationManager.register(requestId, ctx.cancelledFlag());
        try {
            Object callResult = toolInvoker.invoke(requestId, tool, arguments, ctx, session);
            return callResult instanceof ToolResponse tr
                    ? McpJsonSerializer.toolResponseToJson(tr)
                    : McpJsonSerializer.plainTextToolResult(callResult);
        } catch (McpException e) {
            throw withRequestId(requestId, e);
        } finally {
            cancellationManager.unregister(requestId);
        }
    }

    /**
     * Reads a resource by URI.
     *
     * @param requestId the JSON-RPC request id
     * @param params the {@code resources/read} parameters ({@code uri})
     * @param ctx the request context
     * @param session the MCP session, or {@code null}
     * @return the {@code resources/read} result as a JSON object
     */
    public JsonObject readResource(Object requestId, JsonObject params, McpRequestContext ctx, McpSession session) {
        String uri = params != null && params.containsKey("uri") ? params.getString("uri") : null;

        if (uri == null) {
            throw new McpException(requestId, McpErrorCode.INVALID_PARAMS, "Missing resource URI");
        }

        McpResourceDescriptor resource = resourceRegistry
                .findResource(uri)
                .orElseThrow(
                        () -> new McpException(requestId, McpErrorCode.INVALID_PARAMS, "Resource not found: " + uri));

        try {
            Object content =
                    beanInvoker.invoke(requestId, resource.getBeanType(), resource.getMethod(), null, ctx, session);
            JsonObjectBuilder resultBuilder = Json.createObjectBuilder();
            JsonArrayBuilder contentsArray = Json.createArrayBuilder();
            if (content instanceof ResourceResponse rr) {
                rr.getContents().forEach(rc -> contentsArray.add(McpJsonSerializer.resourceContentsToJson(rc)));
            } else {
                String text = content != null ? content.toString() : "";
                contentsArray.add(McpJsonSerializer.plainTextResourceContents(uri, text, resource.getMimeType()));
            }
            resultBuilder.add("contents", contentsArray);
            return resultBuilder.build();
        } catch (McpException e) {
            throw withRequestId(requestId, e);
        }
    }

    /**
     * Renders a prompt by name.
     *
     * @param requestId the JSON-RPC request id
     * @param params the {@code prompts/get} parameters ({@code name}, {@code arguments})
     * @param ctx the request context
     * @param session the MCP session, or {@code null}
     * @return the {@code prompts/get} result as a JSON object
     */
    public JsonObject getPrompt(Object requestId, JsonObject params, McpRequestContext ctx, McpSession session) {
        String promptName = params != null && params.containsKey("name") ? params.getString("name") : null;

        if (promptName == null) {
            throw new McpException(requestId, McpErrorCode.INVALID_PARAMS, "Missing prompt name");
        }

        JsonObject arguments = params.containsKey("arguments") ? params.getJsonObject("arguments") : null;

        McpPromptDescriptor prompt = promptRegistry
                .findPrompt(promptName)
                .orElseThrow(() ->
                        new McpException(requestId, McpErrorCode.INVALID_PARAMS, "Prompt not found: " + promptName));

        try {
            Object callResult =
                    beanInvoker.invoke(requestId, prompt.getBeanType(), prompt.getMethod(), arguments, ctx, session);
            JsonObjectBuilder resultBuilder = Json.createObjectBuilder();
            resultBuilder.add("description", prompt.getDescription());
            JsonArrayBuilder msgsArray = Json.createArrayBuilder();
            if (callResult instanceof PromptResponse pr) {
                pr.messages().forEach(m -> msgsArray.add(McpJsonSerializer.promptMessageToJson(m)));
            } else if (callResult instanceof List<?> messages) {
                for (Object msg : messages) {
                    if (msg instanceof PromptMessage pm) {
                        msgsArray.add(McpJsonSerializer.promptMessageToJson(pm));
                    } else if (msg instanceof McpPromptMessage mpm) {
                        msgsArray.add(McpJsonSerializer.contentBlockMessageToJson(mpm.role(), mpm.content()));
                    } else if (msg != null) {
                        msgsArray.add(McpJsonSerializer.plainTextPromptMessage(msg.toString()));
                    }
                }
            } else {
                String text = callResult != null ? callResult.toString() : "";
                msgsArray.add(McpJsonSerializer.plainTextPromptMessage(text));
            }
            resultBuilder.add("messages", msgsArray);
            return resultBuilder.build();
        } catch (McpException e) {
            throw withRequestId(requestId, e);
        }
    }

    /**
     * Computes completion suggestions for a prompt argument or resource URI.
     *
     * @param requestId the JSON-RPC request id
     * @param params the {@code completion/complete} parameters ({@code ref}, {@code argument})
     * @return the {@code completion/complete} result as a JSON object
     */
    public JsonObject complete(Object requestId, JsonObject params) {
        JsonObject ref = params != null && params.containsKey("ref") ? params.getJsonObject("ref") : null;

        if (ref == null || !ref.containsKey("type")) {
            throw new McpException(requestId, McpErrorCode.INVALID_PARAMS, "Missing completion ref");
        }

        String refType = ref.getString("type");
        String refName = ref.containsKey("name") ? ref.getString("name") : null;
        JsonObject argument = params.containsKey("argument") ? params.getJsonObject("argument") : null;
        String argName = argument != null && argument.containsKey("name") ? argument.getString("name") : null;
        String argValue = argument != null && argument.containsKey("value") ? argument.getString("value") : "";

        List<String> completionValues;
        if ("ref/prompt".equals(refType) && refName != null && argName != null) {
            completionValues = completePromptArgument(refName, argValue);
        } else if ("ref/resource".equals(refType) && refName != null) {
            completionValues = completeResourceUri(argValue);
        } else {
            completionValues = List.of();
        }

        return buildCompletionResultJson(completionValues);
    }

    private List<String> completePromptArgument(String promptName, String prefix) {
        return promptRegistry
                .findPrompt(promptName)
                .map(prompt -> prompt.getArguments().stream()
                        .map(McpPromptDescriptor.PromptArgument::name)
                        .filter(name -> name.startsWith(prefix))
                        .toList())
                .orElse(List.of());
    }

    private List<String> completeResourceUri(String prefix) {
        return resourceRegistry.listResources().stream()
                .map(McpResourceDescriptor::getUri)
                .filter(uri -> uri.startsWith(prefix))
                .toList();
    }

    private JsonObject buildCompletionResultJson(List<String> values) {
        JsonArrayBuilder valuesArray = Json.createArrayBuilder();
        values.forEach(valuesArray::add);
        return Json.createObjectBuilder()
                .add(
                        "completion",
                        Json.createObjectBuilder()
                                .add("values", valuesArray)
                                .add("total", values.size())
                                .add("hasMore", false))
                .build();
    }

    private static McpException withRequestId(Object requestId, McpException e) {
        if (e.getRequestId() != null) {
            return e;
        }
        return new McpException(requestId, e.getErrorCode(), e.getMessage(), e.getHttpStatus(), e.getData());
    }

    private static McpCursor nextCursor(McpPagination.Page<?> page) {
        return page.nextCursor() != null ? new McpCursor(page.nextCursor()) : null;
    }
}
