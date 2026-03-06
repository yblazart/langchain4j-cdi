# Plan PR — `langchain4j-cdi-mcp`

> Module Jakarta EE pur permettant d'exposer des CDI beans `@Tool` comme serveur MCP
> Zero dependance Spring / Quarkus
> Transport : Streamable HTTP (MCP spec 2025-03-26)
> Architecture calquee sur les patterns existants du projet (dual extension, LLMConfig SPI)

---

## 1. Contexte & perimetre

### Ce que le module fait
- Scanner les CDI beans portant des methodes annotees `@Tool` (LangChain4j) au demarrage
- Construire un registre de `McpToolDescriptor` (nom, description, JSON Schema des params)
- Exposer un endpoint JAX-RS implementant le transport **Streamable HTTP MCP**
- Gerer le handshake `initialize / initialized`, `tools/list`, `tools/call`, `ping`

### Ce que le module ne fait PAS (v1)
- `resources/*` et `prompts/*` (post-v1, architecture prevue pour extension)
- Notifications server-initiated spontanees (GET stream optionnel v2)
- Authentification (deleguee au conteneur Jakarta EE / reverse proxy)
- Streaming long des reponses LLM via SSE chunks (v2)

### Transport cible : Streamable HTTP
```
Client -> POST /mcp   Content-Type: application/json
                      Accept: application/json          -> reponse JSON directe
                      Accept: text/event-stream         -> reponse SSE stream

Client -> GET  /mcp   (server-initiated notifications -- v2)
```

---

## 2. Structure des modules Maven

Calquee sur le pattern existant : un parent POM aggregateur (`langchain4j-cdi-mcp`), un module core, et les deux extensions (portable + build-compatible).

```
langchain4j-cdi-mcp/                          ← nouveau module parent (POM aggregateur)
├── pom.xml
├── langchain4j-cdi-mcp-server/               ← core : protocol, registry, schema, transport
│   ├── pom.xml
│   └── src/
│       ├── main/java/dev/langchain4j/cdi/mcp/server/
│       │   ├── protocol/
│       │   │   ├── JsonRpcRequest.java
│       │   │   ├── JsonRpcResponse.java
│       │   │   ├── JsonRpcError.java
│       │   │   ├── McpInitializeParams.java
│       │   │   ├── McpInitializeResult.java
│       │   │   ├── McpServerCapabilities.java
│       │   │   ├── McpImplementation.java
│       │   │   ├── McpToolDescriptor.java
│       │   │   ├── McpToolsListResult.java
│       │   │   ├── McpToolCallParams.java
│       │   │   └── McpToolCallResult.java
│       │   ├── registry/
│       │   │   ├── McpToolRegistry.java       ← @ApplicationScoped, registre central
│       │   │   └── McpToolInvoker.java        ← invocation reflexive via BeanManager
│       │   ├── schema/
│       │   │   └── JsonSchemaGenerator.java   ← JSON Schema depuis Method params
│       │   ├── transport/
│       │   │   ├── McpEndpoint.java           ← @Path("/mcp") JAX-RS resource
│       │   │   ├── McpSessionManager.java     ← sessions actives
│       │   │   ├── McpSession.java            ← etat d'une session MCP
│       │   │   └── McpExceptionMapper.java    ← @Provider JSON-RPC error mapping
│       │   └── error/
│       │       ├── McpErrorCode.java          ← enum codes JSON-RPC + MCP
│       │       ├── McpException.java
│       │       ├── McpSessionException.java
│       │       └── McpToolNotFoundException.java
│       ├── main/resources/
│       │   └── (pas de service file — c'est les extensions qui enregistrent les beans)
│       └── test/java/dev/langchain4j/cdi/mcp/server/
│           ├── protocol/
│           │   └── JsonRpcSerializationTest.java
│           ├── schema/
│           │   └── JsonSchemaGeneratorTest.java
│           ├── registry/
│           │   └── McpToolRegistryTest.java
│           └── fixtures/
│               └── WeatherTool.java
│
├── langchain4j-cdi-mcp-portable-ext/         ← extension portable (WildFly, Payara, GlassFish, Liberty)
│   ├── pom.xml
│   └── src/
│       ├── main/java/dev/langchain4j/cdi/mcp/portableextension/
│       │   └── McpServerPortableExtension.java
│       └── main/resources/META-INF/services/
│           └── jakarta.enterprise.inject.spi.Extension
│
└── langchain4j-cdi-mcp-build-compatible-ext/  ← extension build-compatible (Quarkus, Helidon)
    ├── pom.xml
    └── src/
        ├── main/java/dev/langchain4j/cdi/mcp/buildcompatible/
        │   └── McpServerBuildCompatibleExtension.java
        └── main/resources/META-INF/services/
            └── jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension
```

### Rattachement au projet parent

Le parent POM `langchain4j-cdi-parent` doit inclure le nouveau module :

```xml
<modules>
    <!-- existants -->
    <module>langchain4j-cdi-core</module>
    <module>langchain4j-cdi-portable-ext</module>
    <module>langchain4j-cdi-build-compatible-ext</module>
    <module>langchain4j-cdi-mp</module>
    <!-- nouveau -->
    <module>langchain4j-cdi-mcp</module>
</modules>
```

---

## 3. Dependances Maven

### `langchain4j-cdi-mcp-server/pom.xml`

```xml
<dependencies>
    <!-- LangChain4j : @Tool, @P -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-core</artifactId>
    </dependency>

    <!-- Jakarta EE APIs — provided par le runtime -->
    <dependency>
        <groupId>jakarta.enterprise</groupId>
        <artifactId>jakarta.enterprise.cdi-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>jakarta.ws.rs</groupId>
        <artifactId>jakarta.ws.rs-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>jakarta.json</groupId>
        <artifactId>jakarta.json-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>jakarta.json.bind</groupId>
        <artifactId>jakarta.json.bind-api</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- Test -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### `langchain4j-cdi-mcp-portable-ext/pom.xml`

```xml
<dependencies>
    <dependency>
        <groupId>dev.langchain4j.cdi</groupId>
        <artifactId>langchain4j-cdi-mcp-server</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>jakarta.enterprise</groupId>
        <artifactId>jakarta.enterprise.cdi-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-core</artifactId>
    </dependency>
</dependencies>
```

### `langchain4j-cdi-mcp-build-compatible-ext/pom.xml`

Meme structure que le portable, avec la dependance CDI 4.0 pour `BuildCompatibleExtension`.

**Aucune dependance vers** : `org.springframework.*`, `io.quarkus.*`, `io.projectreactor.*`

---

## 4. Extensions CDI — Dual Pattern

Comme le projet existant, on fournit deux extensions qui partagent la logique core dans `langchain4j-cdi-mcp-server`.

### 4.1 Extension Portable — `McpServerPortableExtension`

Calquee sur `LangChain4JAIServicePortableExtension` :

```java
public class McpServerPortableExtension implements Extension {

    private final List<McpToolCandidate> candidates = new ArrayList<>();

    // Phase 1 : detecter les beans avec methodes @Tool
    <T> void onProcessManagedBean(@Observes ProcessManagedBean<T> pmb) {
        Class<?> beanClass = pmb.getAnnotatedBeanClass().getJavaClass();
        List<Method> toolMethods = Arrays.stream(beanClass.getMethods())
            .filter(m -> m.isAnnotationPresent(Tool.class))
            .toList();

        if (!toolMethods.isEmpty()) {
            candidates.add(new McpToolCandidate(pmb.getBean(), beanClass, toolMethods));
        }
    }

    // Phase 2 : alimenter le registre apres deploiement
    void onAfterDeployment(@Observes AfterDeploymentValidation adv) {
        McpToolRegistry registry = CDI.current().select(McpToolRegistry.class).get();
        for (McpToolCandidate candidate : candidates) {
            for (Method method : candidate.methods()) {
                registry.register(McpToolDescriptor.fromMethod(
                    candidate.beanClass(), method));
            }
        }
    }
}
```

**Service file** `META-INF/services/jakarta.enterprise.inject.spi.Extension` :
```
dev.langchain4j.cdi.mcp.portableextension.McpServerPortableExtension
```

### 4.2 Extension Build-Compatible — `McpServerBuildCompatibleExtension`

Calquee sur `Langchain4JAIServiceBuildCompatibleExtension` :

```java
public class McpServerBuildCompatibleExtension implements BuildCompatibleExtension {

    private final Set<Class<?>> detectedToolBeanClasses = new HashSet<>();

    @Discovery
    public void discovery(ScannedClasses scannedClasses) {
        // Les beans @Tool sont deja des beans CDI normaux,
        // on les detecte ici pour les enregistrer dans le McpToolRegistry
    }

    @Enhancement(types = Object.class)
    public void detectToolBeans(ClassConfig classConfig) {
        Class<?> clazz = classConfig.info().asClass().loadClass();
        boolean hasToolMethods = Arrays.stream(clazz.getMethods())
            .anyMatch(m -> m.isAnnotationPresent(Tool.class));
        if (hasToolMethods) {
            detectedToolBeanClasses.add(clazz);
        }
    }

    @Synthesis
    public void registerMcpBeans(SyntheticComponents syntheticComponents) {
        // Enregistrer un bean synthetique qui peuplera le McpToolRegistry
        // au demarrage avec les classes detectees
        syntheticComponents.addBean(McpToolRegistryPopulator.class)
            .type(McpToolRegistryPopulator.class)
            .scope(ApplicationScoped.class)
            .createWith(McpToolRegistryPopulatorCreator.class)
            .withParam("toolBeanClasses", detectedToolBeanClasses.stream()
                .map(Class::getName).toArray(String[]::new));
    }
}
```

> **Note** : Pour le build-compatible extension, on ne peut pas directement observer `ProcessManagedBean`. On detecte les classes avec `@Tool` pendant `@Enhancement` et on cree un bean synthetique `McpToolRegistryPopulator` qui peuplera le registry au demarrage via `@PostConstruct`.

**Service file** `META-INF/services/jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension` :
```
dev.langchain4j.cdi.mcp.buildcompatible.McpServerBuildCompatibleExtension
```

---

## 5. Configuration — Pattern LLMConfig SPI existant

**Pas de dependance directe vers MicroProfile Config.** On reutilise le pattern existant du projet.

Le `McpEndpoint` et le `McpSessionManager` lisent leur configuration depuis des beans CDI injectables. La configuration peut venir de :

### Option A : Proprietes via LLMConfig (recommande)

Reutiliser le prefix existant :
```properties
dev.langchain4j.cdi.plugin.mcp-server.class=dev.langchain4j.cdi.mcp.server.McpServerConfig
dev.langchain4j.cdi.plugin.mcp-server.config.server-name=langchain4j-cdi
dev.langchain4j.cdi.plugin.mcp-server.config.server-version=1.0.0
dev.langchain4j.cdi.plugin.mcp-server.config.endpoint-path=/mcp
```

Cela cree un bean CDI `McpServerConfig` via le mecanisme `CommonLLMPluginCreator` existant. Le `McpEndpoint` l'injecte via `@Named("mcp-server")`.

### Option B : CDI Producer par l'utilisateur

```java
@Produces @ApplicationScoped
public McpServerConfig mcpConfig() {
    return McpServerConfig.builder()
        .serverName("my-app")
        .serverVersion("2.0.0")
        .endpointPath("/mcp")
        .build();
}
```

### Option C : Defaults sans configuration

Si aucun `McpServerConfig` n'est produit, le `McpEndpoint` utilise des defaults raisonnables :
- `serverName` = `"langchain4j-cdi"`
- `serverVersion` = lecture depuis `META-INF/MANIFEST.MF` ou `"unknown"`
- `endpointPath` = `"/mcp"` (fixe dans l'annotation `@Path`)

```java
public class McpServerConfig {
    private String serverName = "langchain4j-cdi";
    private String serverVersion = "unknown";

    // builder() + getters/setters pour CommonLLMPluginCreator
    public static McpServerConfigBuilder builder() {
        return new McpServerConfigBuilder();
    }
}
```

---

## 6. Couche Protocol — JSON-RPC 2.0 POJOs

### `JsonRpcRequest`
```java
public class JsonRpcRequest {
    private String jsonrpc = "2.0";
    private String id;       // nullable pour notifications
    private String method;   // "initialize", "tools/list", "tools/call", "ping"
    private JsonObject params;
}
```

### `JsonRpcResponse`
```java
public class JsonRpcResponse {
    private String jsonrpc = "2.0";
    private String id;
    private Object result;     // null si error
    private JsonRpcError error; // null si result

    public static JsonRpcResponse success(String id, Object result) { ... }
    public static JsonRpcResponse error(String id, JsonRpcError error) { ... }
}
```

### `McpInitializeResult`
```java
public class McpInitializeResult {
    private String protocolVersion = "2025-03-26";
    private McpServerCapabilities capabilities;
    private McpImplementation serverInfo;
}
```

### `McpToolDescriptor`
```java
public class McpToolDescriptor {
    private String name;
    private String description;
    private JsonObject inputSchema;
    // champs internes (non serialises) :
    private Class<?> beanType;
    private Method method;

    public static McpToolDescriptor fromMethod(Class<?> beanClass, Method method) {
        Tool tool = method.getAnnotation(Tool.class);
        return new McpToolDescriptor(
            tool.name().isEmpty() ? method.getName() : tool.name(),
            tool.value(),
            JsonSchemaGenerator.fromMethod(method),
            beanClass,
            method
        );
    }
}
```

### `McpToolCallResult`
```java
public class McpToolCallResult {
    private List<McpContent> content;
    private boolean isError;

    public static McpToolCallResult text(String text) {
        return new McpToolCallResult(
            List.of(new McpContent("text", text)), false);
    }
}
```

---

## 7. JSON Schema Generator

Genere le `inputSchema` (JSON Schema) depuis la signature Java d'une methode `@Tool`.

```java
public class JsonSchemaGenerator {

    public static JsonObject fromMethod(Method method) {
        // Parcourt les Parameter de la methode
        // Utilise @P pour nom et description des parametres
        // Mappe les types Java vers JSON Schema :
        //   String -> "string"
        //   int/Integer/long/Long -> "integer"
        //   double/Double/float/Float -> "number"
        //   boolean/Boolean -> "boolean"
        //   List/Set -> "array"
        //   Enum -> "string" avec "enum" values
        //   Autre -> "object" (sans proprietes enfants en v1)
        // Tous les parametres non-@Nullable sont dans "required"
    }
}
```

> **Limite v1** : Pas de support des POJOs complexes en profondeur. Extensible via SPI en v2.

---

## 8. Transport JAX-RS — `McpEndpoint`

```java
@Path("/mcp")
@ApplicationScoped
public class McpEndpoint {

    @Inject McpToolRegistry registry;
    @Inject McpSessionManager sessionManager;
    @Inject McpToolInvoker invoker;
    @Inject @Named("mcp-server") Instance<McpServerConfig> configInstance;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.SERVER_SENT_EVENTS})
    public Response handlePost(
            JsonRpcRequest request,
            @HeaderParam("Mcp-Session-Id") String sessionId,
            @HeaderParam("Accept") String accept) {

        return switch (request.getMethod()) {
            case "initialize"                -> handleInitialize(request);
            case "notifications/initialized" -> Response.ok().build();
            case "tools/list"                -> handleToolsList(request, sessionId, wantsSse(accept));
            case "tools/call"                -> handleToolsCall(request, sessionId, wantsSse(accept));
            case "ping"                      -> handlePing(request);
            default                          -> methodNotFound(request);
        };
    }
}
```

### `initialize` — cree la session, retourne capabilities

```java
private Response handleInitialize(JsonRpcRequest req) {
    String newSessionId = sessionManager.createSession(req.getParams());
    McpServerConfig config = resolveConfig();

    McpInitializeResult result = new McpInitializeResult(
        "2025-03-26",
        McpServerCapabilities.withTools(),
        new McpImplementation(config.getServerName(), config.getServerVersion())
    );

    return Response.ok(JsonRpcResponse.success(req.getId(), result))
        .header("Mcp-Session-Id", newSessionId)
        .build();
}
```

### `tools/list` — retourne les descripteurs enregistres

```java
private Response handleToolsList(JsonRpcRequest req, String sessionId, boolean sse) {
    sessionManager.requireSession(sessionId);
    McpToolsListResult result = new McpToolsListResult(
        registry.listTools().stream()
            .map(McpToolDescriptor::toWireFormat)
            .toList()
    );
    return respond(req.getId(), result, sse);
}
```

### `tools/call` — invocation du tool CDI

```java
private Response handleToolsCall(JsonRpcRequest req, String sessionId, boolean sse) {
    sessionManager.requireSession(sessionId);
    McpToolCallParams params = parseParams(req.getParams(), McpToolCallParams.class);

    McpToolDescriptor tool = registry.findTool(params.getName())
        .orElseThrow(() -> new McpToolNotFoundException(req.getId(), params.getName()));

    Object callResult = invoker.invoke(tool, params.getArguments());
    McpToolCallResult result = McpToolCallResult.text(
        callResult != null ? callResult.toString() : "");
    return respond(req.getId(), result, sse);
}
```

### Reponse SSE helper

```java
private Response respond(String id, Object result, boolean sse) {
    JsonRpcResponse rpcResponse = JsonRpcResponse.success(id, result);
    if (!sse) {
        return Response.ok(rpcResponse).build();
    }
    // SSE one-shot : event + fermeture immediate
    String payload = "event: message\ndata: " + toJson(rpcResponse) + "\n\n";
    StreamingOutput stream = out -> {
        out.write(payload.getBytes(StandardCharsets.UTF_8));
        out.flush();
    };
    return Response.ok(stream, MediaType.SERVER_SENT_EVENTS)
        .header("Cache-Control", "no-cache")
        .build();
}
```

---

## 9. Tool Invoker — Resolution CDI

```java
@ApplicationScoped
public class McpToolInvoker {

    @Inject BeanManager bm;

    public Object invoke(McpToolDescriptor descriptor, JsonObject arguments) {
        // 1. Recuperer le bean CDI (pas new !) via BeanManager
        Bean<?> bean = bm.resolve(bm.getBeans(descriptor.getBeanType()));
        CreationalContext<?> ctx = bm.createCreationalContext(bean);
        Object instance = bm.getReference(bean, descriptor.getBeanType(), ctx);

        // 2. Convertir les arguments JSON -> types Java
        Object[] args = resolveArguments(descriptor.getMethod(), arguments);

        // 3. Invoquer
        try {
            return descriptor.getMethod().invoke(instance, args);
        } catch (InvocationTargetException e) {
            throw new McpException(descriptor.getName(), McpErrorCode.INTERNAL_ERROR,
                e.getCause().getMessage());
        }
    }
}
```

---

## 10. Gestion des erreurs

### Codes JSON-RPC 2.0 + extensions MCP

```java
public enum McpErrorCode {
    PARSE_ERROR(-32700),
    INVALID_REQUEST(-32600),
    METHOD_NOT_FOUND(-32601),
    INVALID_PARAMS(-32602),
    INTERNAL_ERROR(-32603),
    // MCP-specific
    SESSION_NOT_FOUND(-32001),
    TOOL_NOT_FOUND(-32002);
}
```

### ExceptionMapper JAX-RS

```java
@Provider
public class McpExceptionMapper implements ExceptionMapper<McpException> {
    @Override
    public Response toResponse(McpException e) {
        // MCP retourne toujours HTTP 200, erreurs dans le payload JSON-RPC
        return Response.ok(JsonRpcResponse.error(
            e.getRequestId(),
            new JsonRpcError(e.getCode().getCode(), e.getMessage())
        )).type(MediaType.APPLICATION_JSON).build();
    }
}
```

---

## 11. Session Manager

```java
@ApplicationScoped
public class McpSessionManager {

    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

    public String createSession(JsonObject initParams) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new McpSession(id, initParams));
        return id;
    }

    public McpSession requireSession(String sessionId) {
        if (sessionId == null || !sessions.containsKey(sessionId)) {
            throw new McpSessionException("Invalid or missing Mcp-Session-Id");
        }
        return sessions.get(sessionId);
    }

    public void terminateSession(String sessionId) {
        sessions.remove(sessionId);
    }
}
```

---

## 12. Strategie de test

### 12.1 Structure des modules de test

Calquee sur la structure existante `langchain4j-cdi-integration-tests/` :

```
langchain4j-cdi-integration-tests/
├── langchain4j-cdi-integration-tests-common/        ← existant (inchange)
├── langchain4j-cdi-integration-tests-mcp-common/    ← NOUVEAU : classes partagees MCP
│   ├── pom.xml
│   └── src/main/java/dev/langchain4j/cdi/mcp/integrationtests/
│       ├── WeatherTool.java                          ← bean @Tool de test
│       ├── CalculatorTool.java                       ← 2eme bean @Tool
│       ├── McpRestService.java                       ← endpoint REST /mcp-test (optionnel, pour test HTTP)
│       └── JaxRsApplication.java                     ← @ApplicationPath("/")
│
├── langchain4j-cdi-integration-tests-mcp-quarkus/   ← NOUVEAU
│   ├── pom.xml
│   └── src/
│       ├── main/resources/application.properties
│       └── test/java/.../McpQuarkusIntegrationTest.java
│
├── langchain4j-cdi-integration-tests-mcp-helidon/   ← NOUVEAU
│   ├── pom.xml
│   └── src/
│       ├── main/resources/META-INF/microprofile-config.properties
│       └── test/java/.../McpHelidonIntegrationTest.java
│
├── langchain4j-cdi-integration-tests-mcp-jakartaee/ ← NOUVEAU (parent POM)
│   ├── pom.xml
│   ├── langchain4j-cdi-integration-tests-mcp-jakartaee-common-wildfly/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/.../McpArquillianTestBase.java
│   │       ├── main/resources/llm-config.properties
│   │       └── test/java/.../McpWildFlyArquillianTest.java
│   ├── langchain4j-cdi-integration-tests-mcp-jakartaee-payara/
│   │   ├── pom.xml
│   │   └── src/test/java/.../McpPayaraArquillianTest.java
│   ├── langchain4j-cdi-integration-tests-mcp-jakartaee-openliberty/
│   │   ├── pom.xml
│   │   └── src/test/java/.../McpOpenLibertyArquillianTest.java
│   └── langchain4j-cdi-integration-tests-mcp-jakartaee-glassfish/   ← NOUVEAU (pas present pour les tests AI existants)
│       ├── pom.xml
│       └── src/test/java/.../McpGlassFishArquillianTest.java
```

### 12.2 Tests unitaires dans `langchain4j-cdi-mcp-server`

| Classe de test | Ce qui est teste |
|---|---|
| `JsonRpcSerializationTest` | Serialisation/deserialisation JSON-RPC request/response avec JSON-B |
| `JsonSchemaGeneratorTest` | Generation du schema pour : String, int, boolean, List, enum, @P annotation, parametre nullable |
| `McpToolRegistryTest` | `register()`, `listTools()`, `findTool()`, doublon de nom |
| `McpToolDescriptorTest` | `fromMethod()` — extraction du nom, description, schema |
| `McpSessionManagerTest` | `createSession()`, `requireSession()` valide/invalide, `terminateSession()` |
| `McpErrorCodeTest` | Mapping codes JSON-RPC |

Frameworks : **JUnit 5 + AssertJ + Mockito** (pas de CDI container pour les tests unitaires purs).

### 12.3 Tests CDI avec Weld SE dans `langchain4j-cdi-mcp-server`

```java
@ExtendWith(WeldJunit5Extension.class)
@AddBeanClasses({McpToolRegistry.class, McpToolInvoker.class, WeatherTool.class})
@AddExtensions(McpServerPortableExtension.class)
class McpToolRegistryCdiTest {

    @Inject McpToolRegistry registry;

    @Test
    void shouldDiscoverWeatherTool() {
        assertThat(registry.listTools()).hasSize(1);
        assertThat(registry.findTool("getWeather")).isPresent();
    }

    @Test
    void shouldInvokeWeatherTool() {
        // via McpToolInvoker
    }
}
```

### 12.4 Tests d'integration — Pattern par runtime

#### Quarkus (`McpQuarkusIntegrationTest`)

```java
@QuarkusTest
class McpQuarkusIntegrationTest {

    @ConfigProperty(name = "quarkus.http.test-port")
    int port;

    @Test
    void shouldCompleteFullMcpHandshake() {
        // 1. initialize
        String sessionId = given()
            .contentType(ContentType.JSON)
            .body(initializeRequest())
            .post("http://localhost:" + port + "/mcp")
            .then().statusCode(200)
            .body("result.protocolVersion", equalTo("2025-03-26"))
            .body("result.capabilities.tools", notNullValue())
            .extract().header("Mcp-Session-Id");

        assertThat(sessionId).isNotBlank();

        // 2. notifications/initialized
        given()
            .contentType(ContentType.JSON)
            .header("Mcp-Session-Id", sessionId)
            .body(initializedNotification())
            .post("http://localhost:" + port + "/mcp")
            .then().statusCode(200);

        // 3. tools/list
        given()
            .contentType(ContentType.JSON)
            .header("Mcp-Session-Id", sessionId)
            .body(toolsListRequest())
            .post("http://localhost:" + port + "/mcp")
            .then().statusCode(200)
            .body("result.tools.name", hasItem("getWeather"));

        // 4. tools/call
        given()
            .contentType(ContentType.JSON)
            .header("Mcp-Session-Id", sessionId)
            .body(toolsCallRequest("getWeather", Map.of("city", "Paris", "unit", "celsius")))
            .post("http://localhost:" + port + "/mcp")
            .then().statusCode(200)
            .body("result.content[0].text", containsString("Paris"));
    }

    @Test
    void shouldReturnErrorForMissingSession() {
        given()
            .contentType(ContentType.JSON)
            .body(toolsListRequest())
            .post("http://localhost:" + port + "/mcp")
            .then().statusCode(200)
            .body("error.code", equalTo(-32001));
    }
}
```

Dependances POM Quarkus :
- `langchain4j-cdi-mcp-server`
- `langchain4j-cdi-mcp-build-compatible-ext`
- `langchain4j-cdi-integration-tests-mcp-common`
- `quarkus-rest` + `quarkus-rest-jsonb` (ou jackson)
- `quarkus-junit5`
- `rest-assured`

#### Helidon (`McpHelidonIntegrationTest`)

```java
@HelidonTest
class McpHelidonIntegrationTest {

    @Inject WebTarget target;

    @Test
    void shouldCompleteFullMcpHandshake() {
        // Meme flow que Quarkus mais via JAX-RS WebTarget
    }
}
```

Dependances : `langchain4j-cdi-mcp-build-compatible-ext` + `helidon-microprofile` + `helidon-microprofile-testing-junit5`

#### WildFly (`McpWildFlyArquillianTest`)

Calque sur `ChatRestServiceArquillianTest` existant :

```java
@ExtendWith(ArquillianExtension.class)
class McpWildFlyArquillianTest {

    @ArquillianResource URL baseURL;

    @Deployment
    static WebArchive createDeployment() {
        // Pattern identique a l'existant :
        // - ShrinkWrap WebArchive
        // - Ajouter classes de test (WeatherTool, JaxRsApplication, etc.)
        // - Inclure manuellement langchain4j-cdi-mcp-server + portable-ext JARs
        // - Maven.resolver() pour deps transitives
        // - META-INF/services/jakarta.enterprise.inject.spi.Extension
        //   -> McpServerPortableExtension
        // - beans.xml
    }

    @Test
    void shouldCompleteFullMcpHandshake() {
        // HTTP calls vers baseURL + "/mcp"
    }
}
```

#### Payara, OpenLiberty, GlassFish

Meme pattern Arquillian que WildFly, avec les specifites de chaque conteneur :
- **Payara** : `payara-arquillian-container` + unpack Payara dist
- **OpenLiberty** : `arquillian-liberty-managed-jakarta` + `server.xml` + unpack Liberty
- **GlassFish** : `arquillian-glassfish-managed` + unpack GlassFish

### 12.5 Bean de test `WeatherTool`

```java
@ApplicationScoped
public class WeatherTool {

    @Tool("Get the current weather for a given city")
    public String getWeather(
            @P("The city name") String city,
            @P("Unit: celsius or fahrenheit") String unit) {
        return "Sunny, 22C in " + city;
    }
}
```

### 12.6 Helper de test — `McpTestRequests`

Classe utilitaire partagee dans `mcp-common` :

```java
public final class McpTestRequests {

    public static String initializeRequest() {
        return """
            {"jsonrpc":"2.0","id":"1","method":"initialize","params":{
                "protocolVersion":"2025-03-26",
                "capabilities":{},
                "clientInfo":{"name":"test-client","version":"1.0"}
            }}""";
    }

    public static String toolsListRequest() {
        return """
            {"jsonrpc":"2.0","id":"2","method":"tools/list","params":{}}""";
    }

    public static String toolsCallRequest(String toolName, Map<String, Object> args) {
        // Genere le JSON-RPC tools/call
    }

    // etc.
}
```

---

## 13. Points d'attention & decisions architecturales

| Sujet | Decision | Justification |
|-------|----------|---------------|
| **Pas de dependance MicroProfile Config** | Configuration via LLMConfig SPI existant ou CDI producer | Coherence avec le reste du projet, portabilite maximale |
| **Pas d'annotation custom `@McpServer`** | On reutilise `@Tool` de LangChain4j directement | Moins d'annotations a maintenir, tous les beans `@Tool` sont exposes |
| **JAX-RS SSE sur POST** | `StreamingOutput` + headers manuels | Compatible WildFly, Payara, GlassFish, Liberty |
| **Serialisation JSON** | `jakarta.json` API (JSON-P) + `jakarta.json.bind` (JSON-B) | Deja fourni par tous les runtimes Jakarta EE |
| **Session en memoire** | `ConcurrentHashMap` dans `McpSessionManager` | Suffisant pour v1, pas de persistance distribuee |
| **Methode `@Tool` return void** | Retourner `McpToolCallResult` avec contenu vide | Conforme a la spec MCP |
| **Decouverte des tools** | CDI Extension scan, pas config properties | Les tools sont des beans CDI existants, pas besoin de config supplementaire |
| **`McpEndpoint` path fixe `/mcp`** | Configurable uniquement via `@ApplicationPath` du runtime | Simplification v1, le path JAX-RS est `/mcp`, le prefix vient du runtime |

### Risques et mitigations

| Risque | Mitigation |
|--------|------------|
| JAX-RS SSE sur POST non standard | `StreamingOutput` + headers manuels, teste sur chaque runtime |
| `BeanManager.getReference()` scope CDI incorrect | Tester avec `@RequestScoped` et `@ApplicationScoped` tools |
| JSON Schema incomplet pour types complexes | Documenter la limite v1, prevoir SPI v2 |
| Concurrence sur `McpSessionManager` | `ConcurrentHashMap`, TTL + cleanup lazy en v2 |
| Build-compatible extension : pas de `ProcessManagedBean` | Utiliser `@Enhancement` + `@Synthesis` avec bean populator synthetique |

---

## 14. Structure de la PR — Commits suggeres

```
feat(mcp): add langchain4j-cdi-mcp module skeleton and Maven POMs
feat(mcp-server): add JSON-RPC 2.0 protocol POJOs
feat(mcp-server): add JsonSchemaGenerator for @Tool method params
feat(mcp-server): add McpToolRegistry and McpToolDescriptor
feat(mcp-server): add McpToolInvoker (CDI bean invocation)
feat(mcp-server): add McpSessionManager
feat(mcp-server): add McpEndpoint JAX-RS (initialize, tools/list, tools/call, ping)
feat(mcp-server): add SSE response support (Streamable HTTP)
feat(mcp-server): add error handling (McpErrorCode, McpExceptionMapper)
feat(mcp-server): add McpServerConfig with LLMConfig SPI support
feat(mcp-portable-ext): add McpServerPortableExtension (tool scanning)
feat(mcp-build-ext): add McpServerBuildCompatibleExtension
test(mcp-server): add unit tests (schema, registry, serialization, session)
test(mcp-server): add Weld SE CDI tests
test(mcp-integration): add common test fixtures (WeatherTool, McpTestRequests)
test(mcp-integration): add Quarkus integration test
test(mcp-integration): add Helidon integration test
test(mcp-integration): add WildFly Arquillian integration test
test(mcp-integration): add Payara Arquillian integration test
test(mcp-integration): add OpenLiberty Arquillian integration test
test(mcp-integration): add GlassFish Arquillian integration test
```

### Checklist PR

- [ ] Aucune dependance compile vers Spring / Quarkus / Reactor
- [ ] `mvn dependency:analyze` propre sur langchain4j-cdi-mcp-server
- [ ] `tools/list` retourne un JSON Schema valide
- [ ] Handshake complet `initialize -> tools/list -> tools/call` passe sur chaque runtime
- [ ] SSE fonctionne avec `Accept: text/event-stream`
- [ ] `Mcp-Session-Id` absent retourne erreur JSON-RPC (pas HTTP 4xx)
- [ ] Compatible Java 17+
- [ ] Spotless formatting passe : `./mvnw spotless:check`
- [ ] Tests unitaires et CDI passent : `./mvnw test -pl langchain4j-cdi-mcp/langchain4j-cdi-mcp-server`
- [ ] Tests d'integration passent sur au moins Quarkus + WildFly

---

## 15. Evolutions post-v1 (backlog)

- `GET /mcp` — stream SSE server-initiated pour notifications
- `resources/list` + `resources/read` — annoter des CDI producers `@McpResource`
- `prompts/list` + `prompts/get` — annoter des methodes `@McpPrompt`
- Streaming long des reponses LLM via SSE chunks
- `JsonSchemaContributor` SPI pour types complexes (POJOs)
- Session TTL + cleanup automatique
- Integration OpenTelemetry pour tracage des `tools/call`
- Support du batching JSON-RPC (tableau de requests)
