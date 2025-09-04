package dev.langchain4j.cdi.core.config.spi;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.util.TypeLiteral;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*
dev.langchain4j.plugin.content-retriever.class=dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
dev.langchain4j.plugin.content-retriever.scope=jakarta.enterprise.context.ApplicationScoped
dev.langchain4j.plugin.content-retriever.config.api-key=${azure.openai.api.key}
dev.langchain4j.plugin.content-retriever.config.endpoint=${azure.openai.endpoint}
dev.langchain4j.plugin.content-retriever.config.embedding-store=lookup:@default
dev.langchain4j.plugin.content-retriever.config.embedding-model=lookup:my-model
 */

/**
 * LLMConfig is the interface for LLM beans configuration.
 * It aims to works lile Smallrye Config, but with pure CDI.
 */
public abstract class LLMConfig {
    static final Map<Class<?>, TypeLiteral<?>> TYPE_LITERALS = new HashMap<>();

    static {
        TYPE_LITERALS.put(EmbeddingStore.class, new TypeLiteral<EmbeddingStore<TextSegment>>() {
        });
    }

    Map<String, ProducerFunction<?>> producers = new ConcurrentHashMap<>();

    /**
     * Prefix for all LLM beans properties.
     */
    public static final String PREFIX = "dev.langchain4j.plugin";

    /**
     * Called by @see LLMConfigProvider.
     */
    public static final String PRODUCER = "defined_bean_producer";
    public static final String CLASS = "class";
    public static final String SCOPE = "scope";

    public abstract void init();

    public abstract Set<String> getPropertyKeys();

    public abstract String getValue(String key);

    /**
     * Get all Langchain4j-cdi LLM beans names, prefixed by PREFIX
     * For example: dev.langchain4j.plugin.content-retriever.class -> content-retriever
     *
     * @return a set of property names
     */
    public Set<String> getBeanNames() {
        return getPropertyKeys().stream()
                .filter(key -> key.startsWith(PREFIX))
                .map(key -> key.substring(PREFIX.length() + 1))
                .map(key -> key.substring(0, key.indexOf(".")))
                .collect(Collectors.toSet());
    }

    public String getBeanPropertyValue(String beanName, String propertyName) {
        String key = PREFIX + "." + beanName + "." + propertyName;
        return getValue(key);
    }

    public void registerProducer(String producersName, ProducerFunction<?> producer) {
        producers.putIfAbsent(producersName, producer);
    }

    public <T> T getBeanPropertyValue(String beanName, String propertyName, Class<T> type) {
        String stringValue = getBeanPropertyValue(beanName, propertyName);
        if (type == ProducerFunction.class && stringValue != null) {
            return type.cast(producers.get(stringValue));
        }
        if (stringValue == null)
            return null;

        if (type == String.class)
            return type.cast(stringValue);
        if (type == Duration.class)
            return type.cast(Duration.parse(stringValue));
        if (type == Integer.class || type == int.class)
            return type.cast(Integer.valueOf(stringValue));
        if (type == Long.class || type == long.class)
            return type.cast(Long.valueOf(stringValue));
        if (type == Boolean.class || type == boolean.class)
            return type.cast(Boolean.valueOf(stringValue));
        if (type == Double.class || type == double.class)
            return type.cast(Double.valueOf(stringValue));
        try {
            return type.getConstructor(String.class).newInstance(stringValue);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported type for value conversion: " + type, e);
        }

    }

    public <T> T getBeanPropertyValue(Instance<Object> lookup, String beanName, String propertyName, Class<T> type) {
        String stringValue = getBeanPropertyValue(beanName, propertyName);

        if (stringValue.startsWith("lookup:")) {
            String lookupableBean = stringValue.substring("lookup:".length());
            Instance<?> inst;
            if ("@default".equals(lookupableBean)) {
                inst = getInstance(lookup, type);
            } else {
                inst = getInstance(lookup, type, lookupableBean);
            }
            return type.cast(inst.get());
        } else {
            return getBeanPropertyValue(beanName, propertyName, type);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> getInstance(Instance<Object> lookup, Class<T> clazz) {
        if (TYPE_LITERALS.containsKey(clazz))
            return (Instance<T>) lookup.select(TYPE_LITERALS.get(clazz));
        return lookup.select(clazz);
    }

    private <T> Instance<T> getInstance(Instance<Object> lookup, Class<T> clazz, String lookupName) {
        if (lookupName == null || lookupName.isBlank())
            return getInstance(lookup, clazz);
        return lookup.select(clazz, NamedLiteral.of(lookupName));
    }

    public Set<String> getPropertyNamesForBean(String beanName) {
        String configPrefix = PREFIX + "." + beanName + ".config.";
        return getPropertyKeys().stream().map(Object::toString)
                .filter(prop -> prop.startsWith(configPrefix))
                .map(prop -> prop.substring(configPrefix.length()))
                .collect(Collectors.toSet());
    }
}
