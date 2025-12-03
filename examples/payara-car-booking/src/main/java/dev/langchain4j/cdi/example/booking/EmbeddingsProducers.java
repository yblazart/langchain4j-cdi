package dev.langchain4j.cdi.example.booking;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class EmbeddingsProducers {

    @Produces
    @ApplicationScoped
    public EmbeddingModel embeddingModel() {
        // Création paresseuse pour éviter le chargement natif pendant le bootstrap CDI.
        return new AllMiniLmL6V2EmbeddingModel();
    }

    // Used by ContentRetriever
    @Produces
    @ApplicationScoped
    public InMemoryEmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}
