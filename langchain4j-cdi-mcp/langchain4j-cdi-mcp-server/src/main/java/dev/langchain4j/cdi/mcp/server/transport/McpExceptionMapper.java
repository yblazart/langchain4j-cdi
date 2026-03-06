package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.error.McpException;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcError;
import dev.langchain4j.cdi.mcp.server.protocol.JsonRpcResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class McpExceptionMapper implements ExceptionMapper<McpException> {

    @Override
    public Response toResponse(McpException e) {
        return Response.ok(JsonRpcResponse.error(
                        e.getRequestId(), new JsonRpcError(e.getErrorCode().getCode(), e.getMessage())))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
