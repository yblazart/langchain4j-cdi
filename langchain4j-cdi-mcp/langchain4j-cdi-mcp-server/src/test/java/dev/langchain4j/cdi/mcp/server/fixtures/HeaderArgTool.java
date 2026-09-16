package dev.langchain4j.cdi.mcp.server.fixtures;

import dev.langchain4j.cdi.mcp.server.api.McpHeader;
import dev.langchain4j.cdi.mcp.server.api.McpLog;
import java.math.BigDecimal;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

/** Tools carrying SEP-2243 {@code x-mcp-header} designations, valid and invalid. */
public class HeaderArgTool {

    @Tool(name = "valid_designations", description = "One designated argument of each permitted primitive type")
    public String valid(
            @ToolArg(name = "tenant", description = "The tenant") @McpHeader("X-Tenant-Id") String tenant,
            @ToolArg(name = "attempt", description = "The attempt") @McpHeader("X-Attempt") int attempt,
            @ToolArg(name = "dryRun", description = "Dry run") @McpHeader("X-Dry-Run") boolean dryRun,
            @ToolArg(name = "plain", description = "Not designated") String plain) {
        return tenant + attempt + dryRun + plain;
    }

    @Tool(name = "empty_designation", description = "Violates: value MUST NOT be empty")
    public String empty(@ToolArg(name = "tenant") @McpHeader("") String tenant) {
        return tenant;
    }

    @Tool(name = "space_designation", description = "Violates: value MUST exclude space")
    public String space(@ToolArg(name = "tenant") @McpHeader("X Tenant") String tenant) {
        return tenant;
    }

    @Tool(name = "colon_designation", description = "Violates: value MUST exclude ':'")
    public String colon(@ToolArg(name = "tenant") @McpHeader("X:Tenant") String tenant) {
        return tenant;
    }

    @Tool(name = "non_ascii_designation", description = "Violates: value MUST be ASCII only")
    public String nonAscii(@ToolArg(name = "tenant") @McpHeader("X-Tenant-Idé") String tenant) {
        return tenant;
    }

    @Tool(name = "duplicate_designation", description = "Violates: value MUST be case-insensitively unique")
    public String duplicate(
            @ToolArg(name = "tenant") @McpHeader("X-Tenant-Id") String tenant,
            @ToolArg(name = "otherTenant") @McpHeader("x-tenant-id") String otherTenant) {
        return tenant + otherTenant;
    }

    @Tool(name = "double_designation", description = "Violates: type number is not permitted")
    public String doubleArg(@ToolArg(name = "amount") @McpHeader("X-Amount") double amount) {
        return String.valueOf(amount);
    }

    @Tool(name = "float_designation", description = "Violates: type number is not permitted")
    public String floatArg(@ToolArg(name = "amount") @McpHeader("X-Amount") float amount) {
        return String.valueOf(amount);
    }

    @Tool(name = "big_decimal_designation", description = "Violates: type number is not permitted")
    public String bigDecimalArg(@ToolArg(name = "amount") @McpHeader("X-Amount") BigDecimal amount) {
        return String.valueOf(amount);
    }

    @Tool(name = "framework_type_designation", description = "Violates: the parameter is not in the input schema")
    public String frameworkArg(@McpHeader("X-Log") McpLog log) {
        return String.valueOf(log);
    }

    @Tool(name = "object_designation", description = "Violates: only primitive types may be designated")
    public String objectArg(@ToolArg(name = "payload") @McpHeader("X-Payload") java.util.List<String> payload) {
        return String.valueOf(payload);
    }
}
