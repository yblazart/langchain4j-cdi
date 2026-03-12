package dev.langchain4j.cdi.spi;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Stereotype
/**
 * Stereotype to register an interface as a LangChain4j AI Service.
 *
 * <p>Apply it on an interface that will be implemented dynamically by LangChain4j AiServices. You can optionally
 * reference named CDI beans to wire the service: models, retrievers, tools, memories, etc. If a property name is blank,
 * the dependency is ignored. For chatModelName, "#default" means select the default bean.
 */
public @interface RegisterAIService {

    Class<? extends Annotation> scope() default RequestScoped.class;

    Class<?>[] tools() default {};

    String chatModelName() default "#default";

    String streamingChatModelName() default "";

    String contentRetrieverName() default "";

    String moderationModelName() default "";

    String chatMemoryName() default "";

    String chatMemoryProviderName() default "";

    String retrievalAugmentorName() default "";

    String toolProviderName() default "";

    /**
     * Input guardrail classes to validate messages before sending to the LLM. If a class is a CDI managed bean, the
     * bean instance is used; otherwise it is instantiated via its no-arg constructor. Mutually exclusive with
     * {@link #inputGuardrailNames()}: if both are specified, only the classes are used and the names are ignored.
     */
    Class<? extends InputGuardrail>[] inputGuardrails() default {};

    /**
     * Output guardrail classes to validate LLM responses before returning them. If a class is a CDI managed bean, the
     * bean instance is used; otherwise it is instantiated via its no-arg constructor. Mutually exclusive with
     * {@link #outputGuardrailNames()}: if both are specified, only the classes are used and the names are ignored.
     */
    Class<? extends OutputGuardrail>[] outputGuardrails() default {};

    /**
     * Named CDI beans implementing {@link InputGuardrail} to validate messages before sending to the LLM. Unresolvable
     * names are skipped with a WARNING log. Mutually exclusive with {@link #inputGuardrails()}: if both are specified,
     * only the classes are used and the names are ignored.
     */
    String[] inputGuardrailNames() default {};

    /**
     * Named CDI beans implementing {@link OutputGuardrail} to validate LLM responses before returning them.
     * Unresolvable names are skipped with a WARNING log. Mutually exclusive with {@link #outputGuardrails()}: if both
     * are specified, only the classes are used and the names are ignored.
     */
    String[] outputGuardrailNames() default {};
}
