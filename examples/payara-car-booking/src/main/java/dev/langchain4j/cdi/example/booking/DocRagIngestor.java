package dev.langchain4j.cdi.example.booking;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments;

import java.io.File;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;

import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

@ApplicationScoped
public class DocRagIngestor {
	
	private static final Logger LOGGER = Logger.getLogger(DocRagIngestor.class.getName());

    @Produces
    public EmbeddingModel embeddingModel() {
        // Création paresseuse pour éviter le chargement natif pendant le bootstrap CDI.
        return new AllMiniLmL6V2EmbeddingModel();
    }

    // Used by ContentRetriever
    @Produces
    public InMemoryEmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    InMemoryEmbeddingStore<TextSegment> embeddingStore;


    private File docsDir = new File(".","docs-for-rag");

    private List<Document> loadDocs() {
        return loadDocuments(docsDir.getPath(), new TextDocumentParser());
    }

    public void ingest(@Observes @Initialized(ApplicationScoped.class) Object pointless) {

        long start = System.currentTimeMillis();

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(300, 30))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        List<Document> docs = loadDocs();
        ingestor.ingest(docs);

        LOGGER.info(String.format("DEMO %d documents ingested in %d msec from %s", docs.size(),
                System.currentTimeMillis() - start,docsDir.getAbsolutePath()));
    }

    public static void main(String[] args) {

        System.out.println(InMemoryEmbeddingStore.class.getInterfaces()[0]);
    }
}
