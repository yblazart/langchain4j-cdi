package dev.langchain4j.cdi.core.config;

import dev.langchain4j.cdi.core.config.spi.LLMConfig;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test utility LLMConfig implementation that reads configuration from a Java 17 text block (multiline string).
 * Example usage:
 * String cfg = """
 * dev.langchain4j.plugin.content-retriever.class=dev.example.MyRetriever
 * dev.langchain4j.plugin.content-retriever.config.api-key=xyz
 * """;
 * LLMConfig config = new TestTextBlockLLMConfig(cfg);
 */
public class TextBlockLLMConfig implements LLMConfig {

    private final Properties properties = new Properties();
    private String textBlock;

    public TextBlockLLMConfig() {
        this("");
    }

    public TextBlockLLMConfig(String textBlock) {
        this.textBlock = textBlock == null ? "" : textBlock;
    }

    public void reinit(String textBlock) {
        this.textBlock = textBlock;
        init();
    }

    @Override
    public void init() {
        try (StringReader reader = new StringReader(textBlock)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> getBeanNames() {
        return properties.keySet().stream().map(Object::toString)
                .filter(prop -> prop.startsWith(PREFIX + "."))
                .map(prop -> prop.substring(PREFIX.length() + 1, prop.indexOf(".", PREFIX.length() + 2)))
                .collect(Collectors.toSet());
    }

    @Override
    public <T> T getBeanPropertyValue(String beanName, String propertyName, Class<T> type) {
        String key = PREFIX + "." + beanName + "." + propertyName;
        String value = properties.getProperty(key);
        if (value == null)
            return null;
        if (type == String.class)
            return type.cast(value);
        if (type == Duration.class)
            return type.cast(Duration.parse(value));
        if (type == Integer.class || type == int.class)
            return type.cast(Integer.valueOf(value));
        if (type == Long.class || type == long.class)
            return type.cast(Long.valueOf(value));
        if (type == Boolean.class || type == boolean.class)
            return type.cast(Boolean.valueOf(value));
        if (type == Double.class || type == double.class)
            return type.cast(Double.valueOf(value));
        try {
            return type.getConstructor(String.class).newInstance(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported type for value conversion: " + type, e);
        }
    }

    @Override
    public Set<String> getPropertyNamesForBean(String beanName) {
        String configPrefix = PREFIX + "." + beanName + ".config.";
        return properties.keySet().stream().map(Object::toString)
                .filter(prop -> prop.startsWith(configPrefix))
                .map(prop -> prop.substring(configPrefix.length()))
                .collect(Collectors.toSet());
    }
}
