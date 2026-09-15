package dev.langchain4j.cdi.mcp.server.registry;

import dev.langchain4j.cdi.mcp.server.error.McpIconProviderException;
import dev.langchain4j.cdi.mcp.server.protocol.McpIconModel;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.mcpjava.server.FeatureType;
import org.mcpjava.server.Icon;
import org.mcpjava.server.IconProvider;
import org.mcpjava.server.Icons;

/**
 * Resolves the icons of an MCP feature from the upstream {@link Icons} annotation, at registration time.
 *
 * <p>{@link Icons} is declared {@code @Target({TYPE, METHOD})}, so it is honoured in exactly those two places: on the
 * feature method itself, and on the class that declares it. A method-level annotation wins; failing that, the
 * annotation is looked up on the CDI bean class and then on the method's declaring class (they differ when the feature
 * is inherited from a superclass).
 *
 * <p>The provider named by {@link Icons#iconProvider()} is resolved as a CDI bean when the container knows one, and
 * instantiated through its no-argument constructor otherwise — which is also what happens in a plain SE unit test,
 * where {@link CDI#current()} has no provider at all. Every failure is a {@link McpIconProviderException} raised from
 * here, i.e. while the feature is being registered, never while a listing is being served.
 */
public final class McpIconResolver {

    private McpIconResolver() {}

    /**
     * Resolves the icons of one feature.
     *
     * @param featureType the kind of feature being registered
     * @param featureName the feature's registered name — the tool, prompt, resource or resource-template <em>name</em>,
     *     which is what {@link IconProvider#getIcons(FeatureType, String)} documents its second argument to be (for a
     *     resource this is the {@code name}, not the {@code uri})
     * @param beanClass the CDI bean class that declares the feature method
     * @param method the feature method
     * @return the icons in wire form, or {@code null} when there is no {@link Icons} annotation or the provider
     *     returned nothing — {@code null} means "emit no {@code icons} key at all"
     * @throws McpIconProviderException if the provider cannot be resolved, is ambiguous, cannot be instantiated, fails,
     *     or yields an icon without a {@code src}
     */
    public static List<McpIconModel> resolve(
            FeatureType featureType, String featureName, Class<?> beanClass, Method method) {
        Icons annotation = findAnnotation(beanClass, method);
        if (annotation == null) {
            return null;
        }
        Class<? extends IconProvider> providerClass = annotation.iconProvider();
        Instance<? extends IconProvider> cdiInstance = select(featureType, featureName, providerClass);
        IconProvider provider = cdiInstance != null
                ? get(featureType, featureName, providerClass, cdiInstance)
                : construct(featureType, featureName, providerClass);
        return toModels(
                featureType, featureName, providerClass, invoke(featureType, featureName, providerClass, provider));
    }

    private static Icons findAnnotation(Class<?> beanClass, Method method) {
        Icons onMethod = method.getAnnotation(Icons.class);
        if (onMethod != null) {
            return onMethod;
        }
        Icons onBean = beanClass != null ? beanClass.getAnnotation(Icons.class) : null;
        if (onBean != null) {
            return onBean;
        }
        Class<?> declaring = method.getDeclaringClass();
        return declaring != beanClass ? declaring.getAnnotation(Icons.class) : null;
    }

    /**
     * Selects the provider as a CDI bean, or returns {@code null} when the container does not know it — or when there
     * is no container at all, which is the case in a plain SE unit test.
     *
     * @param featureType the feature type, for the error message
     * @param featureName the feature name, for the error message
     * @param providerClass the provider to select
     * @return the CDI handle, or {@code null} to fall back to reflective instantiation
     * @throws McpIconProviderException if several beans match
     */
    private static Instance<? extends IconProvider> select(
            FeatureType featureType, String featureName, Class<? extends IconProvider> providerClass) {
        Instance<? extends IconProvider> instance;
        try {
            instance = CDI.current().select(providerClass);
        } catch (RuntimeException e) {
            // no CDI container (SE unit test), or a container that refuses the lookup: fall back to reflection
            return null;
        }
        if (instance.isAmbiguous()) {
            throw new McpIconProviderException(
                    failure(featureType, featureName, providerClass) + " is ambiguous: several CDI beans match it");
        }
        return instance.isUnsatisfied() ? null : instance;
    }

    private static IconProvider get(
            FeatureType featureType,
            String featureName,
            Class<? extends IconProvider> providerClass,
            Instance<? extends IconProvider> instance) {
        try {
            return instance.get();
        } catch (RuntimeException e) {
            throw new McpIconProviderException(
                    failure(featureType, featureName, providerClass) + " could not be obtained from CDI", e);
        }
    }

    private static IconProvider construct(
            FeatureType featureType, String featureName, Class<? extends IconProvider> providerClass) {
        if (providerClass.isInterface() || Modifier.isAbstract(providerClass.getModifiers())) {
            throw new McpIconProviderException(
                    failure(featureType, featureName, providerClass) + " is abstract and is not a CDI bean");
        }
        try {
            Constructor<? extends IconProvider> constructor = providerClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new McpIconProviderException(
                    failure(featureType, featureName, providerClass)
                            + " has no no-argument constructor and is not a CDI bean",
                    e);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new McpIconProviderException(
                    failure(featureType, featureName, providerClass) + " could not be instantiated", e);
        }
    }

    private static List<Icon> invoke(
            FeatureType featureType,
            String featureName,
            Class<? extends IconProvider> providerClass,
            IconProvider provider) {
        try {
            return provider.getIcons(featureType, featureName);
        } catch (RuntimeException e) {
            throw new McpIconProviderException(
                    failure(featureType, featureName, providerClass) + " failed while producing icons", e);
        }
    }

    private static List<McpIconModel> toModels(
            FeatureType featureType,
            String featureName,
            Class<? extends IconProvider> providerClass,
            List<Icon> icons) {
        if (icons == null || icons.isEmpty()) {
            return null;
        }
        return icons.stream()
                .map(icon -> {
                    if (icon == null || icon.src() == null || icon.src().isBlank()) {
                        throw new McpIconProviderException(
                                failure(featureType, featureName, providerClass) + " returned an icon without a 'src'");
                    }
                    return McpIconModel.of(icon);
                })
                .toList();
    }

    private static String failure(
            FeatureType featureType, String featureName, Class<? extends IconProvider> providerClass) {
        return "MCP icons for " + featureType + " '" + featureName + "': icon provider " + providerClass.getName();
    }
}
