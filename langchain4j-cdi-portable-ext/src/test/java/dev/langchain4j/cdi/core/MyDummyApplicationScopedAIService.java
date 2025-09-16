package dev.langchain4j.cdi.core;

import dev.langchain4j.cdi.spi.RegisterAIService;
import jakarta.enterprise.context.ApplicationScoped;

@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@RegisterAIService(scope = ApplicationScoped.class)
public interface MyDummyApplicationScopedAIService {}
