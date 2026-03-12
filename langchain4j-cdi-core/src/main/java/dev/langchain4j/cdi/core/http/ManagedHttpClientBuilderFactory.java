package dev.langchain4j.cdi.core.http;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.CDI;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.InitialContext;

@ApplicationScoped
class ManagedHttpClientBuilderFactory {

    private ExecutorService executorService;
    private static final Logger LOGGER = Logger.getLogger(ManagedHttpClientBuilderFactory.class.getName());

    /**
     * First look up the JEE default managed executor service in JNDI. If unavailable, then look up the MP default
     * managed executor service in CDI.
     */
    @PostConstruct
    public void init() {
        try {
            InitialContext context = null;
            try {
                LOGGER.fine("Retrieving executor service from JNDI");
                context = new InitialContext();
                executorService = (ExecutorService) context.lookup("java:comp/DefaultManagedExecutorService");
                LOGGER.fine("Found executor service from JNDI " + executorService);
            } finally {
                if (context != null) {
                    context.close();
                }
            }
        } catch (Throwable ignored) {
            LOGGER.log(Level.FINE, "Retrieving executor service from JDNI failed", ignored);
            try {
                LOGGER.fine("Retrieving executor service from CDI");
                executorService = (ExecutorService) CDI.current()
                        .select(Class.forName("org.eclipse.microprofile.context.ManagedExecutor"))
                        .get();
                LOGGER.fine("Found executor service from CDI " + executorService);
            } catch (Throwable ignoredAgain) {
                // Can happen when running on non-JEE-server (e.g. Tomcat) or when it is disabled in server config for
                // some reason.
            }
        }
    }

    @Produces
    public HttpClientBuilder create() {
        try {
            // Load JdkHttpClientBuilderFactory class using reflection
            Class<?> factoryClass = Class.forName("dev.langchain4j.http.client.jdk.JdkHttpClientBuilderFactory");

            // Create instance: new JdkHttpClientBuilderFactory()
            Object factory = factoryClass.getDeclaredConstructor().newInstance();

            // Call create() method
            Method createMethod = factoryClass.getMethod("create");
            Object httpClientBuilder = createMethod.invoke(factory);

            // Create HttpClient.Builder with executor: HttpClient.newBuilder().executor(executorService)
            HttpClient.Builder httpBuilder = HttpClient.newBuilder();
            if (executorService != null) {
                httpBuilder.executor(executorService);
            }

            // Call httpClientBuilder(HttpClient.Builder) method
            Method httpClientBuilderMethod =
                    httpClientBuilder.getClass().getMethod("httpClientBuilder", HttpClient.Builder.class);
            return (HttpClientBuilder) httpClientBuilderMethod.invoke(httpClientBuilder, httpBuilder);
        } catch (Exception e) {
            return HttpClientBuilderLoader.loadHttpClientBuilder();
        }
    }
}
