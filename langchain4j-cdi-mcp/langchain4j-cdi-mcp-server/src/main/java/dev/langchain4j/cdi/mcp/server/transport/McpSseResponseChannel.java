package dev.langchain4j.cdi.mcp.server.transport;

import dev.langchain4j.cdi.mcp.server.protocol.McpJsonSerializer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Writes messages as SSE events on the response stream of one request; a write failure cancels the request. */
public class McpSseResponseChannel implements McpResponseChannel {

    private static final Logger LOGGER = Logger.getLogger(McpSseResponseChannel.class.getName());

    private final OutputStream out;
    private final AtomicBoolean cancelledFlag;
    private volatile boolean open = true;

    /**
     * Creates a channel writing SSE events to the given output stream.
     *
     * @param out the response output stream
     * @param cancelledFlag flag set to {@code true} when a write fails, or {@code null}
     */
    public McpSseResponseChannel(OutputStream out, AtomicBoolean cancelledFlag) {
        this.out = out;
        this.cancelledFlag = cancelledFlag;
    }

    @Override
    public void send(Object message) {
        write("event: message\ndata: " + McpJsonSerializer.toJsonValue(message) + "\n\n");
    }

    /**
     * Writes an SSE comment line, typically used as keep-alive.
     *
     * @param comment the comment text
     */
    public void sendComment(String comment) {
        write(": " + comment + "\n\n");
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    private synchronized void write(String payload) {
        if (!open) {
            return;
        }
        try {
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "MCP: SSE response stream closed by client", e);
            open = false;
            if (cancelledFlag != null) {
                cancelledFlag.set(true);
            }
        }
    }
}
