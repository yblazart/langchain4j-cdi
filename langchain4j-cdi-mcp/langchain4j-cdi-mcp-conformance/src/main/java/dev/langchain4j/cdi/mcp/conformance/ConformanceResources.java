package dev.langchain4j.cdi.mcp.conformance;

import dev.langchain4j.cdi.mcp.server.registry.McpResourceRegistry;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceResponse;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;

/** Resources and resource templates expected by the official MCP conformance suite. */
@ApplicationScoped
public class ConformanceResources {

    /** URI of the resource that updates itself so that subscriptions can be observed. */
    public static final String WATCHED_URI = "test://watched-resource";

    private final AtomicInteger watchedCounter = new AtomicInteger();
    private ScheduledExecutorService scheduler;

    @Inject
    McpResourceRegistry resourceRegistry;

    /** Creates a new instance. */
    public ConformanceResources() {}

    /**
     * Starts the ticker that updates the watched resource every three seconds.
     *
     * @param event the CDI container startup event
     */
    public void onStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "conformance-watched-resource");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(
                () -> {
                    watchedCounter.incrementAndGet();
                    resourceRegistry.notifyResourceUpdated(WATCHED_URI);
                },
                3,
                3,
                TimeUnit.SECONDS);
    }

    /** Stops the ticker. */
    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Static text resource.
     *
     * @return the resource text
     */
    @Resource(
            uri = "test://static-text",
            name = "Static Text Resource",
            title = "Static Text Resource",
            description = "A static text resource for testing",
            mimeType = "text/plain")
    public String staticText() {
        return "This is the content of the static text resource.";
    }

    /**
     * Static binary resource.
     *
     * @return the resource contents
     */
    @Resource(
            uri = "test://static-binary",
            name = "Static Binary Resource",
            title = "Static Binary Resource",
            description = "A static binary resource (image) for testing",
            mimeType = "image/png")
    public ResourceResponse staticBinary() {
        return ResourceResponse.of("test://static-binary", ConformanceFixtures.testImage(), "image/png");
    }

    /**
     * Resource used as the target of the embedded-resource fixtures.
     *
     * @return the resource text
     */
    @Resource(
            uri = "test://embedded-resource",
            name = "Embedded Resource",
            title = "Embedded Resource",
            description = "The resource embedded by the embedded-resource fixtures",
            mimeType = "text/plain")
    public String embeddedResource() {
        return "This is an embedded resource content.";
    }

    /**
     * Resource used as the target of the mixed-content fixtures.
     *
     * @return the resource text
     */
    @Resource(
            uri = "test://mixed-content-resource",
            name = "Mixed Content Resource",
            title = "Mixed Content Resource",
            description = "The resource embedded by the mixed-content fixture",
            mimeType = "application/json")
    public String mixedContentResource() {
        return "{\"test\":\"data\",\"value\":123}";
    }

    /**
     * Resource that changes every three seconds.
     *
     * @return the resource text
     */
    @Resource(
            uri = WATCHED_URI,
            name = "Watched Resource",
            title = "Watched Resource",
            description = "A resource that auto-updates every 3 seconds",
            mimeType = "text/plain")
    public String watchedResource() {
        return "Watched resource content: " + watchedCounter.get();
    }

    /**
     * Resource template with a single parameter.
     *
     * @param id the template parameter
     * @return the rendered JSON document
     */
    @ResourceTemplate(
            uriTemplate = "test://template/{id}/data",
            name = "Resource Template",
            title = "Resource Template",
            description = "A resource template with parameter substitution",
            mimeType = "application/json")
    public String template(@ResourceTemplateArg(name = "id") String id) {
        return "{\"id\":\"" + id + "\",\"templateTest\":true,\"data\":\"Data for ID: " + id + "\"}";
    }
}
