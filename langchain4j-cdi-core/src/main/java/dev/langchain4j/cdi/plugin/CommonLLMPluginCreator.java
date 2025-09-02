package dev.langchain4j.cdi.plugin;

import dev.langchain4j.cdi.core.config.spi.LLMConfig;
import dev.langchain4j.cdi.core.config.spi.LLMConfigProvider;
import dev.langchain4j.cdi.core.config.spi.ProducerFunction;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static dev.langchain4j.cdi.core.config.spi.LLMConfig.PRODUCER;

/*
dev.langchain4j.plugin.content-retriever.class=dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
dev.langchain4j.plugin.content-retriever.scope=jakarta.enterprise.context.ApplicationScoped
dev.langchain4j.plugin.content-retriever.config.api-key=${azure.openai.api.key}
dev.langchain4j.plugin.content-retriever.config.endpoint=${azure.openai.endpoint}
dev.langchain4j.plugin.content-retriever.config.embedding-store=lookup:default
dev.langchain4j.plugin.content-retriever.config.embedding-model=lookup:my-model
 */

/**
 * Help to build LangChain4j Beans from LLMConfig.
 * A typical class (model, chat, etc...) often follows this pattern (@see dev.langchain4j.model.ollama.OllamaChatModel for
 * example):
 * Inside the class there is a static method to create the builder:
 * public static OllamaChatModelBuilder builder() { }
 * This builder is also defined as a static inner class.
 * And then the builder class has private fields for each property of the bean. And an associated setter method for each field
 * (without 'set' prefix).
 *
 */
public class CommonLLMPluginCreator {

    public static final Logger LOGGER = Logger.getLogger(CommonLLMPluginCreator.class.getName());

    private static final Map<Class<?>, TypeLiteral<?>> TYPE_LITERALS = new HashMap<>();
    private static final Set<PluginPropertyConverter> CONVERTERS = Set.of(new ChatListenerPluginPropertyConverter(),
            new CapabilitiesPluginPropertyConverter());

    static {
        TYPE_LITERALS.put(EmbeddingStore.class, new TypeLiteral<EmbeddingStore<TextSegment>>() {
        });
    }

    @SuppressWarnings("unchecked")
    public static void createAllLLMBeans(LLMConfig llmConfig, Consumer<BeanData> beanBuilder) throws ClassNotFoundException {
        Set<String> beanNameToCreate = llmConfig.getBeanNames();
        LOGGER.info("detected beans to create : " + beanNameToCreate);

        for (String beanName : beanNameToCreate) {
            String className = llmConfig.getBeanPropertyValue(beanName, "class", String.class);
            String scopeClassName = llmConfig.getBeanPropertyValue(beanName, "scope", String.class);
            if (scopeClassName == null || scopeClassName.isBlank()) {
                scopeClassName = ApplicationScoped.class.getName();
            }
            Class<? extends Annotation> scopeClass = (Class<? extends Annotation>) loadClass(scopeClassName);
            Class<?> targetClass = loadClass(className);
            ProducerFunction<Object> producer = llmConfig.getBeanPropertyValue(beanName, PRODUCER,
                    ProducerFunction.class);
            if (producer != null) {
                beanBuilder.accept(
                        new BeanData(targetClass, null, scopeClass, beanName,
                                (Instance<Object> creationalContext) -> producer.produce(creationalContext, beanName)));
            } else {
                // test if there is an inner static class Builder
                Class<?> builderCLass = Arrays.stream(targetClass.getDeclaredClasses())
                        .filter(declClass -> declClass.getName().endsWith("Builder")).findFirst().orElse(null);
                LOGGER.info("Builder class : " + builderCLass);
                if (builderCLass == null) {
                    LOGGER.warning("No builder class found, cancel " + beanName);
                    return;
                }
                beanBuilder.accept(
                        new BeanData(targetClass, builderCLass, scopeClass, beanName,
                                (Instance<Object> creationalContext) -> CommonLLMPluginCreator.create(
                                        creationalContext,
                                        llmConfig,
                                        beanName,
                                        targetClass,
                                        builderCLass)));
            }
        }
    }

    public record BeanData(Class<?> targetClass,
            Class<?> builderClass,
            Class<? extends Annotation> scopeClass,
            String beanName,
            Function<Instance<Object>, Object> callback) {

    }

    public static Object create(Instance<Object> lookup, String beanName, Class<?> targetClass, Class<?> builderClass) {
        return create(lookup, LLMConfigProvider.getLlmConfig(), beanName, targetClass, builderClass);
    }

    public static Object create(Instance<Object> lookup, LLMConfig llmConfig, String beanName, Class<?> targetClass,
            Class<?> builderClass) {
        LOGGER.info(
                "Create instance for :" + beanName + ", target class : " + targetClass + ", builderClass : " + builderClass);
        String currentProperty = "";
        try {
            Object builder = targetClass.getMethod("builder").invoke(null);
            Set<String> properties = llmConfig.getPropertyNamesForBean(beanName);
            for (String property : properties) {
                currentProperty = property;
                String camelCaseProperty = LLMConfig.dashToCamel(property);
                // determine field in builder
                List<Field> fields = getFieldsInAllHierarchy(builderClass);
                Field propertyFieldInBuilder = fields.stream()
                        .filter(field -> field.getName().equals(camelCaseProperty))
                        .findFirst()
                        .orElse(null);
                if (propertyFieldInBuilder == null)
                    throw new NoSuchFieldException(
                            "Can't find Field for property '" + property + "' (" + camelCaseProperty + ")");
                //
                Method setterMethod = builderClass.getMethod(camelCaseProperty, propertyFieldInBuilder.getType());
                String propertyKey = "config." + property;
                boolean applied = false;
                for (PluginPropertyConverter converter : CONVERTERS) {
                    if (converter.satisfies(targetClass, builderClass, camelCaseProperty)) {
                        Object value = converter.convert(lookup, targetClass, builderClass, beanName, propertyKey, llmConfig);
                        LOGGER.fine("Attempt to feed : " + property + " (" + camelCaseProperty + ") with : "
                                + value);
                        setterMethod.invoke(builder, value);
                        applied = true;
                    }
                }

                if (!applied) {
                    String stringValue = llmConfig.getBeanPropertyValue(beanName, propertyKey, String.class);
                    LOGGER.fine("Attempt to feed : " + property + " (" + camelCaseProperty + ") with : " + stringValue);

                    Class<?> parameterType = propertyFieldInBuilder.getType();
                    if (stringValue.startsWith("lookup:")) {
                        String lookupableBean = stringValue.substring("lookup:".length());
                        LOGGER.info("Lookup " + lookupableBean + " " + parameterType);
                        Instance<?> inst;
                        if ("default".equals(lookupableBean)) {
                            inst = getInstance(lookup, parameterType);
                        } else {
                            inst = getInstance(lookup, parameterType, lookupableBean);
                        }
                        setterMethod.invoke(builder, inst.get());
                    } else {
                        Object value = llmConfig.getBeanPropertyValue(beanName, propertyKey, parameterType);
                        setterMethod.invoke(builder, value);
                    }
                }
            }
            return builderClass.getMethod("build").invoke(builder);
        } catch (
                IllegalAccessException | IllegalArgumentException | NoSuchMethodException | SecurityException
                | NoSuchFieldException | InvocationTargetException e) {
            throw new RuntimeException("Current property : " + currentProperty, e);
        }
    }

    private static List<Field> getFieldsInAllHierarchy(Class<?> startClass) {

        List<Field> currentClassFields = new ArrayList<>(Arrays.asList(startClass.getDeclaredFields()));
        Class<?> parentClass = startClass.getSuperclass();

        if (parentClass != null && !parentClass.equals(Object.class)) {
            List<Field> parentClassFields = getFieldsInAllHierarchy(parentClass);
            currentClassFields.addAll(parentClassFields);
        }

        return currentClassFields;
    }

    private static Class<?> loadClass(String className) throws ClassNotFoundException {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException classNotFoundException) {
            return CommonLLMPluginCreator.class.getClassLoader().loadClass(className);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Instance<T> getInstance(Instance<Object> lookup, Class<T> clazz) {
        if (TYPE_LITERALS.containsKey(clazz))
            return (Instance<T>) lookup.select(TYPE_LITERALS.get(clazz));
        return lookup.select(clazz);
    }

    private static <T> Instance<T> getInstance(Instance<Object> lookup, Class<T> clazz, String lookupName) {
        if (lookupName == null || lookupName.isBlank())
            return getInstance(lookup, clazz);
        return lookup.select(clazz, NamedLiteral.of(lookupName));
    }

    interface PluginPropertyConverter {

        boolean satisfies(final Class<?> beanClass, final Class<?> builderClass, final String propertyName);

        Object convert(final Instance<Object> lookup, final Class<?> beanClass, final Class<?> builderClass,
                final String beanName, final String key, final LLMConfig config);
    }

    static class ChatListenerPluginPropertyConverter implements PluginPropertyConverter {

        private static final String PROPERTY_NAME = "listeners";

        @Override
        public boolean satisfies(Class<?> beanClass, Class<?> builderClass, String propertyName) {
            // TODO Auto-generated method stub
            if (ChatModel.class.isAssignableFrom(beanClass) || StreamingChatModel.class.isAssignableFrom(beanClass)) {
                return PROPERTY_NAME.equals(propertyName) && Arrays.stream(builderClass.getDeclaredFields())
                        .anyMatch(field -> field.getName().equals(propertyName));
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object convert(Instance<Object> lookup, Class<?> beanClass, Class<?> builderClass, String beanName,
                final String key,
                LLMConfig config) {
            // TODO Auto-generated method stub
            final Field declaredField = Arrays.stream(builderClass.getDeclaredFields())
                    .filter(field -> field.getName().equals(PROPERTY_NAME)).findFirst().get();
            Class<?> typeParameterClass = null;
            if (declaredField.getGenericType() instanceof ParameterizedType) {
                ParameterizedType pType = (ParameterizedType) declaredField.getGenericType();
                typeParameterClass = (Class<?>) pType.getActualTypeArguments()[0];
            } else
                typeParameterClass = ChatModelListener.class;

            List<Object> listeners = (List<Object>) Collections.checkedList(new ArrayList<>(),
                    typeParameterClass);
            String value = config.getBeanPropertyValue(beanName, key, String.class);
            if ("@all".equals(value.trim())) {
                Instance<Object> inst = (Instance<Object>) getInstance(lookup, typeParameterClass);
                if (inst != null) {
                    inst.forEach(listeners::add);
                }
            } else {
                try {
                    String[] values = config.getBeanPropertyValue(beanName, key, String[].class);
                    for (String className : values) {
                        Instance<?> inst = getInstance(lookup, loadClass(className.trim()));
                        listeners.add(inst.get());
                    }
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            if (listeners != null && !listeners.isEmpty()) {
                listeners.stream().forEach(l -> LOGGER.info("Adding listener: " + l.getClass().getName()));
            }

            return listeners;
        }
    }

    static class CapabilitiesPluginPropertyConverter implements PluginPropertyConverter {

        private static final String PROPERTY_NAME = "supportedCapabilities";

        @Override
        public boolean satisfies(Class<?> beanClass, Class<?> builderClass, String propertyName) {
            // TODO Auto-generated method stub
            if (ChatModel.class.isAssignableFrom(beanClass) || StreamingChatModel.class.isAssignableFrom(beanClass)) {
                return PROPERTY_NAME.equals(propertyName) && Arrays.stream(builderClass.getDeclaredFields())
                        .anyMatch(field -> field.getName().equals(propertyName));
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object convert(Instance<Object> lookup, Class<?> beanClass, Class<?> builderClass, String beanName,
                final String key,
                LLMConfig config) {
            final Field declaredField = Arrays.stream(builderClass.getDeclaredFields())
                    .filter(field -> field.getName().equals(PROPERTY_NAME)).findFirst().get();
            Class<?> typeParameterClass;
            if (declaredField.getGenericType() instanceof ParameterizedType pType) {
                typeParameterClass = (Class<?>) pType.getActualTypeArguments()[0];
            } else
                typeParameterClass = Capability.class;

            try {
                Set<Object> enums = (Set<Object>) Collections.checkedSet(new HashSet<>(),
                        typeParameterClass);
                String[] values = config.getBeanPropertyValue(beanName, key, String[].class);
                for (String enumString : values) {
                    enums.add(toEnum(enumString));
                }

                return enums;
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @SuppressWarnings("unchecked")
        private <T extends Enum<T>> Enum<T> toEnum(String value) throws ClassNotFoundException {
            int lastDotIndex = value.lastIndexOf(".");
            Class<T> enumClass = (Class<T>) Class.forName(value.substring(0, lastDotIndex));
            return Enum.valueOf(enumClass, value.substring(lastDotIndex + 1));
        }
    }
}
