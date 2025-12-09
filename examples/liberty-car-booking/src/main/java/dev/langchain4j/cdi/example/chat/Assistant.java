package dev.langchain4j.cdi.example.chat;

import dev.langchain4j.cdi.spi.RegisterAIService;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAIService(
        chatModelName = "chat-assistant",
        scope = ApplicationScoped.class,
        chatMemoryProviderName = "#default")
interface Assistant {

    String chat(@MemoryId String sessionId, @UserMessage String userMessage);
}
