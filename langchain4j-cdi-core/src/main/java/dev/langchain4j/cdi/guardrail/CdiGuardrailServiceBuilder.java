package dev.langchain4j.cdi.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.classloading.ClassMetadataProvider;
import dev.langchain4j.guardrail.Guardrail;
import dev.langchain4j.guardrail.GuardrailRequest;
import dev.langchain4j.guardrail.GuardrailResult;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.config.InputGuardrailsConfig;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.service.guardrail.GuardrailService;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import jakarta.enterprise.inject.spi.CDI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A CDI-aware implementation of {@link GuardrailService.Builder} that resolves guardrail instances as CDI managed
 * beans.
 *
 * <p>When guardrail classes are referenced in {@code @InputGuardrails} or {@code @OutputGuardrails} annotations, this
 * builder will attempt to look them up from the CDI container first. If a bean is not resolvable via CDI, it falls back
 * to instantiation via the no-arg constructor.
 *
 * <p>This allows guardrail implementations to use CDI features such as {@code @Inject}, interceptors, decorators, and
 * scoping.
 */
final class CdiGuardrailServiceBuilder implements GuardrailService.Builder {

    private static final Logger LOGGER = Logger.getLogger(CdiGuardrailServiceBuilder.class.getName());

    private final Supplier<InputGuardrailExecutor> defaultInputGuardrailSupplier =
            () -> InputGuardrailExecutor.builder()
                    .config(this.inputGuardrailsConfig)
                    .guardrails(
                            getNonAnnotationBasedClassLevelGuardrails(this.inputGuardrails, this.inputGuardrailClasses))
                    .build();

    private final Supplier<OutputGuardrailExecutor> defaultOutputGuardrailSupplier =
            () -> OutputGuardrailExecutor.builder()
                    .config(this.outputGuardrailsConfig)
                    .guardrails(getNonAnnotationBasedClassLevelGuardrails(
                            this.outputGuardrails, this.outputGuardrailClasses))
                    .build();

    private final Class<?> aiServiceClass;
    private InputGuardrailsConfig inputGuardrailsConfig;
    private OutputGuardrailsConfig outputGuardrailsConfig;
    private List<Class<? extends InputGuardrail>> inputGuardrailClasses = new ArrayList<>();
    private List<Class<? extends OutputGuardrail>> outputGuardrailClasses = new ArrayList<>();
    private List<InputGuardrail> inputGuardrails = new ArrayList<>();
    private List<OutputGuardrail> outputGuardrails = new ArrayList<>();

    CdiGuardrailServiceBuilder(Class<?> aiServiceClass) {
        this.aiServiceClass = ensureNotNull(aiServiceClass, "aiServiceClass");
    }

    @Override
    public GuardrailService.Builder inputGuardrailsConfig(InputGuardrailsConfig config) {
        this.inputGuardrailsConfig = ensureNotNull(config, "config");
        return this;
    }

    @Override
    public GuardrailService.Builder outputGuardrailsConfig(OutputGuardrailsConfig config) {
        this.outputGuardrailsConfig = ensureNotNull(config, "config");
        return this;
    }

    @Override
    public <I extends InputGuardrail> GuardrailService.Builder inputGuardrailClasses(
            List<Class<? extends I>> guardrailClasses) {
        this.inputGuardrailClasses.clear();
        if (guardrailClasses != null) {
            this.inputGuardrailClasses.addAll(guardrailClasses);
        }
        return this;
    }

    @Override
    public <O extends OutputGuardrail> GuardrailService.Builder outputGuardrailClasses(
            List<Class<? extends O>> guardrailClasses) {
        this.outputGuardrailClasses.clear();
        if (guardrailClasses != null) {
            this.outputGuardrailClasses.addAll(guardrailClasses);
        }
        return this;
    }

    @Override
    public <I extends InputGuardrail> GuardrailService.Builder inputGuardrails(List<I> guardrails) {
        this.inputGuardrails.clear();
        if (guardrails != null) {
            this.inputGuardrails.addAll(guardrails);
        }
        return this;
    }

    @Override
    public <O extends OutputGuardrail> GuardrailService.Builder outputGuardrails(List<O> guardrails) {
        this.outputGuardrails.clear();
        if (guardrails != null) {
            this.outputGuardrails.addAll(guardrails);
        }
        return this;
    }

    @Override
    public GuardrailService build() {
        var inputGuardrailsByMethod = new HashMap<Object, InputGuardrailExecutor>();
        var outputGuardrailsByMethod = new HashMap<Object, OutputGuardrailExecutor>();
        var factory = ClassMetadataProvider.getClassMetadataProviderFactory();

        factory.getNonStaticMethodsOnClass(this.aiServiceClass).forEach(method -> {
            var inputGuardrailsForMethod = computeInputGuardrailsForAiServiceMethod(method, factory);
            var outputGuardrailsForMethod = computeOutputGuardrailsForAiServiceMethod(method, factory);

            if (!inputGuardrailsForMethod.guardrails().isEmpty()) {
                inputGuardrailsByMethod.put(method, inputGuardrailsForMethod);
            }

            if (!outputGuardrailsForMethod.guardrails().isEmpty()) {
                outputGuardrailsByMethod.put(method, outputGuardrailsForMethod);
            }
        });

        return new CdiGuardrailService(this.aiServiceClass, inputGuardrailsByMethod, outputGuardrailsByMethod);
    }

    /**
     * Resolves a guardrail instance from the CDI container. If the guardrail class is not a CDI managed bean, falls
     * back to instantiation via the no-arg constructor.
     */
    static <P extends GuardrailRequest, R extends GuardrailResult<R>, G extends Guardrail<P, R>> G getGuardrailInstance(
            Class<G> guardrailClass) {
        ensureNotNull(guardrailClass, "guardrailClass");
        try {
            var instance = CDI.current().select(guardrailClass);
            if (instance.isResolvable()) {
                return instance.get();
            }
        } catch (IllegalStateException e) {
            LOGGER.log(
                    Level.FINE,
                    "CDI container not available, falling back to reflection for " + guardrailClass.getName(),
                    e);
        }
        try {
            return guardrailClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to create guardrail instance for " + guardrailClass.getName(), e);
        }
    }

    private static <P extends GuardrailRequest, R extends GuardrailResult<R>, G extends Guardrail<P, R>>
            List<G> getNonAnnotationBasedClassLevelGuardrails(
                    List<G> guardrails, List<Class<? extends G>> guardrailClasses) {
        ensureNotNull(guardrails, "guardrails");
        ensureNotNull(guardrailClasses, "guardrailClasses");

        var guardrailsSetByBuilderAtClassLevel = guardrails.stream();
        var guardrailsSetByBuilderAtClassLevelByClassName =
                guardrailClasses.stream().map(CdiGuardrailServiceBuilder::getGuardrailInstance);

        return Stream.concat(guardrailsSetByBuilderAtClassLevel, guardrailsSetByBuilderAtClassLevelByClassName)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @SuppressWarnings("unchecked")
    private static <I extends InputGuardrail> List<I> getGuardrails(InputGuardrails inputGuardrails) {
        return Stream.of(inputGuardrails.value())
                .map(guardrailClass -> (I) getGuardrailInstance(guardrailClass))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static <O extends OutputGuardrail> List<O> getGuardrails(OutputGuardrails outputGuardrails) {
        return Stream.of(outputGuardrails.value())
                .map(guardrailClass -> (O) getGuardrailInstance(guardrailClass))
                .toList();
    }

    // InputGuardrails annotation has no config properties (unlike OutputGuardrails which has maxRetries),
    // so we simply build the default config.
    private static InputGuardrailsConfig computeConfig(InputGuardrails annotation) {
        return InputGuardrailsConfig.builder().build();
    }

    private static OutputGuardrailsConfig computeConfig(OutputGuardrails annotation) {
        return OutputGuardrailsConfig.builder()
                .maxRetries(annotation.maxRetries())
                .build();
    }

    private InputGuardrailExecutor computeInputGuardrails(InputGuardrails annotation) {
        return InputGuardrailExecutor.builder()
                .config(hasInputGuardrailConfigSetOnBuilder() ? this.inputGuardrailsConfig : computeConfig(annotation))
                .guardrails(
                        hasInputGuardrailsSetOnBuilder()
                                ? getNonAnnotationBasedClassLevelGuardrails(
                                        this.inputGuardrails, this.inputGuardrailClasses)
                                : getGuardrails(annotation))
                .build();
    }

    private OutputGuardrailExecutor computeOutputGuardrails(OutputGuardrails annotation) {
        return OutputGuardrailExecutor.builder()
                .config(
                        hasOutputGuardrailConfigSetOnBuilder()
                                ? this.outputGuardrailsConfig
                                : computeConfig(annotation))
                .guardrails(
                        hasOutputGuardrailsSetOnBuilder()
                                ? getNonAnnotationBasedClassLevelGuardrails(
                                        this.outputGuardrails, this.outputGuardrailClasses)
                                : getGuardrails(annotation))
                .build();
    }

    private <MethodKey> InputGuardrailExecutor computeInputGuardrailsForAiServiceMethod(
            MethodKey method, ClassMetadataProviderFactory<MethodKey> factory) {
        if (inputGuardrailsAndConfigSetOnBuilder()) {
            return this.defaultInputGuardrailSupplier.get();
        }

        return factory.getAnnotation(method, InputGuardrails.class)
                .map(this::computeInputGuardrails)
                .orElseGet(() -> factory.getAnnotation(this.aiServiceClass, InputGuardrails.class)
                        .map(this::computeInputGuardrails)
                        .orElseGet(this.defaultInputGuardrailSupplier::get));
    }

    private <MethodKey> OutputGuardrailExecutor computeOutputGuardrailsForAiServiceMethod(
            MethodKey method, ClassMetadataProviderFactory<MethodKey> factory) {
        if (outputGuardrailsAndConfigSetOnBuilder()) {
            return this.defaultOutputGuardrailSupplier.get();
        }

        return factory.getAnnotation(method, OutputGuardrails.class)
                .map(this::computeOutputGuardrails)
                .orElseGet(() -> factory.getAnnotation(this.aiServiceClass, OutputGuardrails.class)
                        .map(this::computeOutputGuardrails)
                        .orElseGet(this.defaultOutputGuardrailSupplier::get));
    }

    private boolean hasInputGuardrailsSetOnBuilder() {
        return !this.inputGuardrails.isEmpty() || !this.inputGuardrailClasses.isEmpty();
    }

    private boolean hasInputGuardrailConfigSetOnBuilder() {
        return this.inputGuardrailsConfig != null;
    }

    private boolean hasOutputGuardrailsSetOnBuilder() {
        return !this.outputGuardrails.isEmpty() || !this.outputGuardrailClasses.isEmpty();
    }

    private boolean hasOutputGuardrailConfigSetOnBuilder() {
        return this.outputGuardrailsConfig != null;
    }

    private boolean inputGuardrailsAndConfigSetOnBuilder() {
        return hasInputGuardrailsSetOnBuilder() && hasInputGuardrailConfigSetOnBuilder();
    }

    private boolean outputGuardrailsAndConfigSetOnBuilder() {
        return hasOutputGuardrailsSetOnBuilder() && hasOutputGuardrailConfigSetOnBuilder();
    }
}
