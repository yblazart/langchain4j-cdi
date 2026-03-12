package dev.langchain4j.cdi.guardrail;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.service.guardrail.GuardrailService;
import org.junit.jupiter.api.Test;

class CdiGuardrailServiceBuilderFactoryTest {

    interface SimpleAiService {
        String chat(String message);
    }

    @Test
    void getBuilder_returnsNonNullBuilder() {
        var factory = new CdiGuardrailServiceBuilderFactory();
        GuardrailService.Builder builder = factory.getBuilder(SimpleAiService.class);
        assertNotNull(builder);
    }

    @Test
    void getBuilder_returnedBuilderProducesCdiGuardrailService() {
        var factory = new CdiGuardrailServiceBuilderFactory();
        GuardrailService service = factory.getBuilder(SimpleAiService.class).build();
        assertNotNull(service);
        assertEquals(SimpleAiService.class, service.aiServiceClass());
        assertInstanceOf(CdiGuardrailService.class, service);
    }
}
