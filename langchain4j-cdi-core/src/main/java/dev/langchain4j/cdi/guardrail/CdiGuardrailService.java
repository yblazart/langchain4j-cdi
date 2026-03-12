package dev.langchain4j.cdi.guardrail;

import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.service.guardrail.AbstractGuardrailService;
import java.util.Map;

/**
 * A CDI-aware {@link dev.langchain4j.service.guardrail.GuardrailService} implementation whose guardrail instances are
 * resolved from the CDI container.
 *
 * <p>Instances are created by {@link CdiGuardrailServiceBuilder#build()}.
 */
final class CdiGuardrailService extends AbstractGuardrailService {

    CdiGuardrailService(
            Class<?> aiServiceClass,
            Map<Object, InputGuardrailExecutor> inputGuardrails,
            Map<Object, OutputGuardrailExecutor> outputGuardrails) {
        super(aiServiceClass, inputGuardrails, outputGuardrails);
    }
}
