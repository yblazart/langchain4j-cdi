package dev.langchain4j.cdi.mcp.server.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Writes messages as SSE events on the response stream of one request; a write failure cancels the request. */
public class McpSseResponseChannel implements McpSseChannel {

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
    public void sendData(String json) {
        write("event: message\ndata: " + json + "\n\n");
    }

    /**
     * Writes an SSE comment line, typically used as keep-alive.
     *
     * @param comment the comment text
     */
    @Override
    public void sendComment(String comment) {
        write(": " + comment + "\n\n");
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    /**
     * Stops writing to the stream. The output stream itself belongs to the Jakarta REST runtime, which ends the
     * response when the streaming entity returns.
     */
    @Override
    public void close() {
        open = false;
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
