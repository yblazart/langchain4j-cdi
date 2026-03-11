package dev.langchain4j.cdi.aiservice;

import dev.langchain4j.cdi.spi.RegisterAIService;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility to build LangChain4j AiServices proxies from CDI beans and the @RegisterAIService metadata.
 *
 * <p>The method create() inspects the provided service interface for @RegisterAIService and tries to resolve optional
 * collaborating beans from the CDI container (by name or default): - ChatModel or StreamingChatModel - ContentRetriever
 * or RetrievalAugmentor (RetrievalAugmentor has priority) - ToolProvider, or if missing, instantiate tools classes
 * declared in the annotation via no-arg constructors - ChatMemory or ChatMemoryProvider - ModerationModel -
 * InputGuardrails and OutputGuardrails (by class or named CDI beans)
 *
 * <p>Only the components that are resolvable are wired into the AiServices builder.
 */
public class CommonAIServiceCreator {

    private static final Logger LOGGER = Logger.getLogger(CommonAIServiceCreator.class.getName());
    private static final String DEFAULT_BEAN_NAME = "#default";

    /**
     * Create a LangChain4j AI service proxy for the given annotated interface.
     *
     * @param lookup CDI Instance used to resolve named beans (models, tools, memories, etc.).
     * @param interfaceClass the AI service interface annotated with {@link dev.langchain4j.cdi.spi.RegisterAIService}.
     * @return a runtime proxy implementing the given interface.
     */
    public static <X> X create(Instance<Object> lookup, Class<X> interfaceClass) {
        RegisterAIService annotation = interfaceClass.getAnnotation(RegisterAIService.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Interface " + interfaceClass.getName() + " must be annotated with @RegisterAIService");
        }
        String chatModelName = annotation.chatModelName();
        String streamingChatModelName = annotation.streamingChatModelName();
        // Instances
        Instance<ChatModel> chatModelInstance = resolveChatModel(lookup, chatModelName, streamingChatModelName);
        Instance<StreamingChatModel> streamingChatModel =
                getInstance(lookup, StreamingChatModel.class, streamingChatModelName);
        Instance<ContentRetriever> contentRetriever =
                getInstance(lookup, ContentRetriever.class, annotation.contentRetrieverName());
        Instance<RetrievalAugmentor> retrievalAugmentor =
                getInstance(lookup, RetrievalAugmentor.class, annotation.retrievalAugmentorName());
        Instance<ToolProvider> toolProvider = getInstance(lookup, ToolProvider.class, annotation.toolProviderName());

        AiServices<X> builder = AiServices.builder(interfaceClass);
        if (chatModelInstance != null && chatModelInstance.isResolvable()) {
            LOGGER.fine("ChatModel " + chatModelInstance.get());
            builder.chatModel(chatModelInstance.get());
        }
        if (streamingChatModel != null && streamingChatModel.isResolvable()) {
            LOGGER.fine("StreamingChatModel " + streamingChatModel.get());
            builder.streamingChatModel(streamingChatModel.get());
        }
        // AiServices requires only one of [retriever, contentRetriever, retrievalAugmentor].
        // If a RetrievalAugmentor is provided, prefer it and do not set ContentRetriever.
        if (retrievalAugmentor != null && retrievalAugmentor.isResolvable()) {
            LOGGER.fine("RetrievalAugmentor " + retrievalAugmentor.get());
            builder.retrievalAugmentor(retrievalAugmentor.get());
        } else if (contentRetriever != null && contentRetriever.isResolvable()) {
            LOGGER.fine("ContentRetriever " + contentRetriever.get());
            builder.contentRetriever(contentRetriever.get());
        }
        if (toolProvider != null && toolProvider.isResolvable()) {
            LOGGER.fine("ToolProvider " + toolProvider.get());
            builder.toolProvider(toolProvider.get());
        } else if (annotation.tools().length > 0) {
            List<Object> tools = new ArrayList<>(annotation.tools().length);
            for (Class<?> toolClass : annotation.tools()) {
                try {
                    // First, check CDI.
                    Instance<? extends Object> toolInstance = lookup.select(toolClass);
                    if (toolInstance != null && toolInstance.isResolvable()) {
                        tools.add(toolInstance.get());
                    } else {
                        tools.add(toolClass.getConstructor((Class<?>[]) null).newInstance((Object[]) null));
                    }
                } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                    LOGGER.log(
                            Level.SEVERE,
                            "Failed to create tool " + toolClass + " for " + interfaceClass + ", skipping: "
                                    + ex.getMessage(),
                            ex);
                }
            }
            builder.tools(tools);
        }
        Instance<ChatMemory> chatMemory = getInstance(lookup, ChatMemory.class, annotation.chatMemoryName());
        if (chatMemory != null && chatMemory.isResolvable()) {
            ChatMemory chatMemoryInstance = chatMemory.get();
            LOGGER.fine("ChatMemory " + chatMemoryInstance);
            builder.chatMemory(chatMemoryInstance);
        }

        Instance<ChatMemoryProvider> chatMemoryProvider =
                getInstance(lookup, ChatMemoryProvider.class, annotation.chatMemoryProviderName());
        if (chatMemoryProvider != null && chatMemoryProvider.isResolvable()) {
            LOGGER.fine("ChatMemoryProvider " + chatMemoryProvider.get());
            builder.chatMemoryProvider(chatMemoryProvider.get());
        }

        Instance<ModerationModel> moderationModelInstance =
                getInstance(lookup, ModerationModel.class, annotation.moderationModelName());
        if (moderationModelInstance != null && moderationModelInstance.isResolvable()) {
            LOGGER.fine("ModerationModel " + moderationModelInstance.get());
            builder.moderationModel(moderationModelInstance.get());
        }
        List<InputGuardrail> inputGuardrails = new ArrayList<>();
        if (annotation.inputGuardrails().length > 0 && annotation.inputGuardrailNames().length > 0) {
            LOGGER.log(
                    Level.WARNING,
                    "Both inputGuardrails and inputGuardrailNames specified for {0}. Using inputGuardrails classes and ignoring inputGuardrailNames.",
                    interfaceClass.getName());
        }
        if (annotation.inputGuardrails().length > 0) {
            inputGuardrails.addAll(resolveGuardrails(lookup, annotation.inputGuardrails()));
        } else if (annotation.inputGuardrailNames().length > 0) {
            inputGuardrails.addAll(resolveGuardrails(lookup, InputGuardrail.class, annotation.inputGuardrailNames()));
        }
        if (!inputGuardrails.isEmpty()) {
            LOGGER.fine("InputGuardrails " + inputGuardrails);
            builder.inputGuardrails(inputGuardrails);
        }
        List<OutputGuardrail> outputGuardrails = new ArrayList<>();
        if (annotation.outputGuardrails().length > 0 && annotation.outputGuardrailNames().length > 0) {
            LOGGER.log(
                    Level.WARNING,
                    "Both outputGuardrails and outputGuardrailNames specified for {0}. Using outputGuardrails classes and ignoring outputGuardrailNames.",
                    interfaceClass.getName());
        }
        if (annotation.outputGuardrails().length > 0) {
            outputGuardrails.addAll(resolveGuardrails(lookup, annotation.outputGuardrails()));
        } else if (annotation.outputGuardrailNames().length > 0) {
            outputGuardrails.addAll(
                    resolveGuardrails(lookup, OutputGuardrail.class, annotation.outputGuardrailNames()));
        }
        if (!outputGuardrails.isEmpty()) {
            LOGGER.fine("OutputGuardrails " + outputGuardrails);
            builder.outputGuardrails(outputGuardrails);
        }
        return builder.build();
    }

    /**
     * Resolve ChatModel with fallback to default if no named instance and no streaming model configured. If a named
     * ChatModel is not resolvable and no StreamingChatModel is configured, try to resolve the default ChatModel to
     * satisfy AiServices requirement.
     */
    private static Instance<ChatModel> resolveChatModel(
            Instance<Object> lookup, String chatModelName, String streamingChatModelName) {
        Instance<ChatModel> chatModelInstance = getInstance(lookup, ChatModel.class, chatModelName);

        // If neither ChatModel nor StreamingChatModel is configured, try default ChatModel
        if ((chatModelInstance == null || !chatModelInstance.isResolvable())
                && (streamingChatModelName == null || streamingChatModelName.isBlank())) {
            return lookup.select(ChatModel.class);
        }

        return chatModelInstance;
    }

    /**
     * Resolve guardrail instances by class. For each class, first attempts CDI lookup; if the bean is not resolvable,
     * falls back to instantiation via the no-arg constructor. Classes that fail both resolution paths are skipped with
     * a WARNING log.
     *
     * @param lookup CDI Instance used for bean resolution.
     * @param guardrailClasses the guardrail classes to resolve.
     * @param <G> the guardrail type (InputGuardrail or OutputGuardrail).
     * @return a list of resolved guardrail instances, in declaration order.
     */
    private static <G> List<G> resolveGuardrails(Instance<Object> lookup, Class<? extends G>[] guardrailClasses) {
        List<G> guardrails = new ArrayList<>(guardrailClasses.length);
        for (Class<? extends G> guardrailClass : guardrailClasses) {
            try {
                Instance<? extends G> guardrailInstance = lookup.select(guardrailClass);
                if (guardrailInstance != null && guardrailInstance.isResolvable()) {
                    guardrails.add(guardrailInstance.get());
                } else {
                    guardrails.add(
                            guardrailClass.getConstructor((Class<?>[]) null).newInstance((Object[]) null));
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to create guardrail " + guardrailClass + ", skipping: " + ex.getMessage(),
                        ex);
            }
        }
        return guardrails;
    }

    /**
     * Resolve guardrail instances by named CDI bean lookup. Each name is resolved via {@link #getInstance(Instance,
     * Class, String)}. Names that cannot be resolved are skipped with a WARNING log.
     *
     * @param lookup CDI Instance used for bean resolution.
     * @param type the guardrail interface class (e.g. InputGuardrail.class).
     * @param guardrailNames the CDI bean names to resolve.
     * @param <G> the guardrail type (InputGuardrail or OutputGuardrail).
     * @return a list of resolved guardrail instances, in declaration order.
     */
    private static <G> List<G> resolveGuardrails(Instance<Object> lookup, Class<G> type, String[] guardrailNames) {
        List<G> guardrails = new ArrayList<>(guardrailNames.length);
        for (String guardrailName : guardrailNames) {
            try {
                Instance<? extends G> guardrailInstance = getInstance(lookup, type, guardrailName);
                if (guardrailInstance != null && guardrailInstance.isResolvable()) {
                    guardrails.add(guardrailInstance.get());
                } else {
                    LOGGER.log(Level.WARNING, "Named guardrail ''{0}'' is not resolvable, skipping", guardrailName);
                }
            } catch (IllegalArgumentException ex) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to resolve guardrail '" + guardrailName + "', skipping: " + ex.getMessage(),
                        ex);
            }
        }
        return guardrails;
    }

    /**
     * Resolve a CDI Instance for the given type and name. If name is "#default", select the default bean of the given
     * type. If name is blank or null, returns null (meaning: do not attempt to resolve).
     */
    private static <X> Instance<X> getInstance(Instance<Object> lookup, Class<X> type, String name) {
        LOGGER.fine("CDI get instance of type '" + type + "' with name '" + name + "'");
        if (name != null && !name.isBlank()) {
            if (DEFAULT_BEAN_NAME.equals(name)) {
                return lookup.select(type);
            }

            return lookup.select(type, NamedLiteral.of(name));
        }

        return null;
    }
}
