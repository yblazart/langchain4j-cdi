This example is based on a simplified car booking application inspired from the [Java meets AI](https://www.youtube.com/watch?v=BD1MSLbs9KE) talk from [Lize Raes](https://www.linkedin.com/in/lize-raes-a8a34110/) at Devoxx Belgium 2023 with additional work from [Jean-François James](http://jefrajames.fr/). The original demo is from [Dmytro Liubarskyi](https://www.linkedin.com/in/dmytro-liubarskyi/).

To test please do

use ./run.sh 

then
the webpage should appearr
http://127.0.0.1:8080/ to send your questions

# ATTENTION

due to some DLL reload problems on payara, [DocRagIngestor.java](src/main/java/dev/langchain4j/cdi/example/booking/DocRagIngestor.java)  has bean changed
to use @Producer method and then @Inject to get the bean.


```java
@ApplicationScoped
public class DocRagIngestor {
	
	private static final Logger LOGGER = Logger.getLogger(DocRagIngestor.class.getName());

// ... existing code ...
    // Used by ContentRetriever
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
// ... existing code ...

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

        if (!docsDir.exists()) {
            LOGGER.warn(String.format("DEMO dossier inexistant, ingestion ignorée: %s", docsDir.getAbsolutePath()));
            return;
        }

        List<Document> docs = loadDocs();
        ingestor.ingest(docs);

        LOGGER.info(String.format("DEMO %d documents ingérés en %d ms depuis %s", docs.size(),
                System.currentTimeMillis() - start,docsDir.getAbsolutePath()));
    }

    public static void main(String[] args) {

        System.out.println(InMemoryEmbeddingStore.class.getInterfaces()[0]);
    }
}


```