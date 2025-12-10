package dev.langchain4j.cdi.example.chat;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;

@ApplicationScoped
public class ChatAgent {

    private static final String CHAT_MEMORY_CDI_NAME = "chat-ai-service-memory";

    @Produces
    private ChatMemoryProvider chatMemoryProvider;

    @PostConstruct
    private void init() {
        chatMemoryProvider = sessionId -> CDI.current()
                .select(ChatMemory.class, NamedLiteral.of(CHAT_MEMORY_CDI_NAME))
                .get();
    }

    @Inject
    private Assistant assistant = null;

    public Assistant getAssistant() {
        if (assistant == null) {
            assistant = CDI.current().select(Assistant.class).get();
        }
        return assistant;
    }

    public String chat(String sessionId, String message) {
        String reply = getAssistant().chat(sessionId, message).trim();
        int i = reply.lastIndexOf(message);
        return i > 0 ? reply.substring(i) : reply;
    }
}
