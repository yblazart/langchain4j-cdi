package dev.langchain4j.cdi.mcp.server.transport;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseBroadcaster;
import java.lang.reflect.Type;

/** In-memory {@link Sse} producing simple outbound events, for unit tests without a Jakarta REST runtime. */
class FakeSse implements Sse {

    @Override
    public OutboundSseEvent.Builder newEventBuilder() {
        return new Builder();
    }

    @Override
    public SseBroadcaster newBroadcaster() {
        throw new UnsupportedOperationException();
    }

    record Event(String name, String comment, Object data) implements OutboundSseEvent {

        @Override
        public Class<?> getType() {
            return data != null ? data.getClass() : null;
        }

        @Override
        public Type getGenericType() {
            return getType();
        }

        @Override
        public MediaType getMediaType() {
            return null;
        }

        @Override
        public Object getData() {
            return data;
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getComment() {
            return comment;
        }

        @Override
        public long getReconnectDelay() {
            return RECONNECT_NOT_SET;
        }

        @Override
        public boolean isReconnectDelaySet() {
            return false;
        }
    }

    static final class Builder implements OutboundSseEvent.Builder {
        private String name;
        private String comment;
        private Object data;

        @Override
        public OutboundSseEvent.Builder id(String id) {
            return this;
        }

        @Override
        public OutboundSseEvent.Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder reconnectDelay(long milliseconds) {
            return this;
        }

        @Override
        public OutboundSseEvent.Builder mediaType(MediaType mediaType) {
            throw new AssertionError("media type must not be set: some runtimes emit it as a non-standard SSE field");
        }

        @Override
        public OutboundSseEvent.Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder data(Class type, Object data) {
            this.data = data;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder data(GenericType type, Object data) {
            this.data = data;
            return this;
        }

        @Override
        public OutboundSseEvent.Builder data(Object data) {
            this.data = data;
            return this;
        }

        @Override
        public OutboundSseEvent build() {
            return new Event(name, comment, data);
        }
    }
}
