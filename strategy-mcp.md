# Plan PR — `langchain4j-cdi-mcp-server`

> Module Jakarta EE pur permettant d'exposer des CDI beans comme serveur MCP  
> Zéro dépendance Spring / Quarkus  
> Transport : Streamable HTTP (MCP spec 2025-03-26)

---

## 1. Contexte & périmètre

### Ce que le module fait
- Scanner les CDI beans portant des méthodes annotées `@Tool` (LangChain4j)
- Construire un registre de `McpTool` descriptors au démarrage de l'application
- Exposer un endpoint JAX-RS implémentant le transport **Streamable HTTP MCP**
- Gérer le handshake `initialize / initialized`, `tools/list`, `tools/call`

### Ce que le module ne fait PAS (v1)
- `resources/*` et `prompts/*` (post-v1, architecture prévue pour extension)
- Notifications server-initiated spontanées (GET stream optionnel v2)
- Authentification (délégué au conteneur Jakarta EE / reverse proxy)

### Transport cible : Streamable HTTP
```
Client → POST /mcp   Content-Type: application/json
                     Accept: application/json          → réponse JSON directe
                     Accept: text/event-stream         → réponse SSE stream

Client → GET  /mcp   (server-initiated notifications — v2)
```

---

## 2. Structure du module Maven

```
langchain4j-cdi-mcp-server/
├── pom.xml
└── src/
    ├── main/java/dev/langchain4j/cdi/mcp/server/
    │   ├── annotations/
    │   │   ├── McpServer.java               ← marque un CDI bean comme contributeur MCP
    │   │   └── McpServerInfo.java           ← qualifier pour la config (nom, version)
    │   ├── protocol/
    │   │   ├── JsonRpcRequest.java          ← POJO JSON-RPC 2.0 request
    │   │   ├── JsonRpcResponse.java         ← POJO JSON-RPC 2.0 response
    │   │   ├── JsonRpcError.java            ← error object
    │   │   ├── McpInitializeParams.java     ← params de la method "initialize"
    │   │   ├── McpInitializeResult.java     ← résultat initialize (capabilities)
    │   │   ├── McpServerCapabilities.java   ← objet capabilities
    │   │   ├── McpToolDescriptor.java       ← descriptor d'un tool (name, desc, schema)
    │   │   ├── McpToolsListResult.java      ← résultat tools/list
    │   │   ├── McpToolCallParams.java       ← params tools/call
    │   │   └── McpToolCallResult.java       ← résultat tools/call
    │   ├── registry/
    │   │   ├── McpToolRegistry.java         ← @ApplicationScoped, registre central
    │   │   └── McpToolInvoker.java          ← invocation réflexive du CDI bean
    │   ├── extension/
    │   │   └── McpServerExtension.java      ← CDI Extension (scan au boot)
    │   ├── schema/
    │   │   └── JsonSchemaGenerator.java     ← génère le JSON Schema depuis Method params
    │   └── transport/
    │       ├── McpEndpoint.java             ← @Path("/mcp") JAX-RS resource
    │       ├── McpSessionManager.java       ← @ApplicationScoped, sessions actives
    │       └── McpSession.java             ← état d'une session MCP (post-initialize)
    ├── main/resources/
    │   └── META-INF/
    │       └── services/
    │           └── jakarta.enterprise.inject.spi.Extension  ← enregistrement CDI Extension
    └── test/
        ├── java/dev/langchain4j/cdi/mcp/server/
        │   ├── McpToolRegistryTest.java
        │   ├── McpEndpointIT.java           ← test d'intégration avec Arquillian / RestAssured
        │   └── fixtures/
        │       └── WeatherTool.java         ← bean de test avec @Tool
        └── resources/
            └── arquillian.xml
```

---

## 3. Dépendances Maven (`pom.xml`)

```xml
<dependencies>
    <!-- LangChain4j core : @Tool, @P, ToolSpecification -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-core</artifactId>
    </dependency>

    <!-- Jakarta EE APIs uniquement — provided par le runtime -->
    <dependency>
        <groupId>jakarta.enterprise</groupId>
        <artifactId>jakarta.enterprise.cdi-api</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>jakarta.ws.rs</groupId>
        <artifactId>jakarta.ws.rs-api</artifactId>
        <scope>provided</scope>
        <!-- JAX-RS 3.1 minimum pour SSE sur POST (AsyncResponse + StreamingOutput) -->
    </dependency>
    <dependency>
        <groupId>jakarta.json.bind</groupId>
        <artifactId>jakarta.json.bind-api</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- Test scope -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <scope>test</scope>
    </dependency>
    <!-- Arquillian ou Weld SE pour tests CDI en isolation -->
    <dependency>
        <groupId>org.jboss.weld.se</groupId>
        <artifactId>weld-se-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Aucune dépendance vers :**
- `org.springframework.*`
- `io.quarkus.*`
- `io.projectreactor.*`
- `com.squareup.okhttp3.*`

---

## 4. Couche Protocol — JSON-RPC 2.0 POJOs

### 4.1 `JsonRpcRequest`
```java
public class JsonRpcRequest {
    private String jsonrpc = "2.0";   // toujours "2.0"
    private String id;                 // String ou Long (nullable pour notifications)
    private String method;             // "initialize", "tools/list", "tools/call", ...
    private JsonObject params;         // jakarta.json.JsonObject
    // getters / setters / @JsonbPropertyOrder
}
```

### 4.2 `JsonRpcResponse`
```java
public class JsonRpcResponse {
    private String jsonrpc = "2.0";
    private String id;
    private Object result;   // null si error
    private JsonRpcError error; // null si result
}
```

### 4.3 `McpInitializeResult`
```java
public class McpInitializeResult {
    private String protocolVersion = "2025-03-26";
    private McpServerCapabilities capabilities;
    private McpImplementation serverInfo;
}

public class McpServerCapabilities {
    private McpToolsCapability tools; // { "listChanged": false }
}

public class McpImplementation {
    private String name;    // "langchain4j-cdi"
    private String version; // depuis pom.properties ou MicroProfile Config
}
```

### 4.4 `McpToolDescriptor`
```java
public class McpToolDescriptor {
    private String name;
    private String description;
    private JsonObject inputSchema; // JSON Schema généré depuis la signature Java
}
```

---

## 5. CDI Extension — `McpServerExtension`

Le rôle de l'extension : **au boot**, scanner tous les beans CDI ayant des méthodes `@Tool`, construire les `McpToolDescriptor` et les enregistrer dans le `McpToolRegistry`.

```java
public class McpServerExtension implements Extension {

    private final List<McpToolCandidate> candidates = new ArrayList<>();

    // 1. Observer ProcessManagedBean : détecter les beans avec @Tool
    <T> void onProcessManagedBean(
            @Observes ProcessManagedBean<T> pmb, BeanManager bm) {

        Class<?> beanClass = pmb.getAnnotatedBeanClass().getJavaClass();
        List<Method> toolMethods = Arrays.stream(beanClass.getMethods())
            .filter(m -> m.isAnnotationPresent(Tool.class))
            .toList();

        if (!toolMethods.isEmpty()) {
            candidates.add(new McpToolCandidate(pmb.getBean(), beanClass, toolMethods));
        }
    }

    // 2. Observer AfterDeploymentValidation : alimenter le registry
    void onAfterDeployment(
            @Observes AfterDeploymentValidation adv, BeanManager bm) {

        McpToolRegistry registry = CDI.current().select(McpToolRegistry.class).get();
        for (McpToolCandidate candidate : candidates) {
            for (Method method : candidate.methods()) {
                Tool tool = method.getAnnotation(Tool.class);
                McpToolDescriptor descriptor = McpToolDescriptor.builder()
                    .name(tool.name().isEmpty() ? method.getName() : tool.name())
                    .description(tool.value())  // valeur de @Tool
                    .inputSchema(JsonSchemaGenerator.fromMethod(method))
                    .beanType(candidate.beanClass())
                    .method(method)
                    .build();
                registry.register(descriptor);
            }
        }
    }
}
```

**Enregistrement** dans `META-INF/services/jakarta.enterprise.inject.spi.Extension` :
```
dev.langchain4j.cdi.mcp.server.extension.McpServerExtension
```

---

## 6. Registry — `McpToolRegistry`

```java
@ApplicationScoped
public class McpToolRegistry {

    private final Map<String, McpToolDescriptor> tools = new ConcurrentHashMap<>();

    public void register(McpToolDescriptor descriptor) {
        tools.put(descriptor.getName(), descriptor);
    }

    public Collection<McpToolDescriptor> listTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public Optional<McpToolDescriptor> findTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }
}
```

---

## 7. JSON Schema Generator

Composant clé : générer le `inputSchema` (JSON Schema Draft-7) depuis la signature Java d'une méthode.

```java
@ApplicationScoped
public class JsonSchemaGenerator {

    public static JsonObject fromMethod(Method method) {
        JsonObjectBuilder schema = Json.createObjectBuilder()
            .add("type", "object");
        JsonObjectBuilder properties = Json.createObjectBuilder();
        JsonArrayBuilder required = Json.createArrayBuilder();

        for (Parameter param : method.getParameters()) {
            String paramName = resolveParamName(param); // @P ou nom réflexif
            String description = param.isAnnotationPresent(P.class)
                ? param.getAnnotation(P.class).value() : "";

            properties.add(paramName, buildPropertySchema(param.getType(), description));

            if (!param.getType().isPrimitive() &&
                !param.isAnnotationPresent(Nullable.class)) {
                required.add(paramName);
            }
        }

        return schema
            .add("properties", properties)
            .add("required", required)
            .build();
    }

    private static JsonObject buildPropertySchema(Class<?> type, String description) {
        // String → "string", int/Integer → "integer",
        // boolean/Boolean → "boolean", List → "array", etc.
        // Support basique pour v1, extensible via SPI en v2
    }
}
```

> **Note** : Pour v1, support des types scalaires Java + `String` + `List`. Les types complexes (POJO) génèrent `"type": "object"` sans propriétés enfants — extensible via un `JsonSchemaContributor` SPI en v2.

---

## 8. Transport JAX-RS — `McpEndpoint`

C'est le cœur du module. Un seul endpoint `@Path("/mcp")`.

### 8.1 Dispatch JSON-RPC

```java
@Path("/mcp")
@ApplicationScoped
public class McpEndpoint {

    @Inject McpToolRegistry registry;
    @Inject McpSessionManager sessionManager;
    @Inject McpToolInvoker invoker;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handlePost(
            JsonRpcRequest request,
            @HeaderParam("Mcp-Session-Id") String sessionId,
            @HeaderParam("Accept") String accept) {

        boolean wantsStream = MediaType.SERVER_SENT_EVENTS.equals(accept);

        return switch (request.getMethod()) {
            case "initialize"        -> handleInitialize(request, sessionId);
            case "notifications/initialized" -> Response.ok().build(); // notification, no response
            case "tools/list"        -> handleToolsList(request, sessionId, wantsStream);
            case "tools/call"        -> handleToolsCall(request, sessionId, wantsStream);
            case "ping"              -> handlePing(request);
            default                  -> methodNotFound(request);
        };
    }
}
```

### 8.2 `initialize`

```java
private Response handleInitialize(JsonRpcRequest req, String sessionId) {
    String newSessionId = sessionManager.createSession(req.getParams());

    McpInitializeResult result = McpInitializeResult.builder()
        .protocolVersion("2025-03-26")
        .serverInfo(new McpImplementation("langchain4j-cdi", readVersion()))
        .capabilities(McpServerCapabilities.withTools())
        .build();

    return Response.ok(JsonRpcResponse.success(req.getId(), result))
        .header("Mcp-Session-Id", newSessionId)
        .build();
}
```

### 8.3 `tools/list` — réponse JSON classique

```java
private Response handleToolsList(JsonRpcRequest req, String sessionId, boolean stream) {
    sessionManager.requireSession(sessionId); // 401 si session inconnue

    McpToolsListResult result = new McpToolsListResult(
        registry.listTools().stream()
            .map(McpToolDescriptor::toMcpTool)
            .toList()
    );

    if (stream) {
        // SSE wrapping : event: message\ndata: {...}\n\n
        return sseResponse(req.getId(), result);
    }
    return Response.ok(JsonRpcResponse.success(req.getId(), result)).build();
}
```

### 8.4 `tools/call` — invocation + réponse

```java
private Response handleToolsCall(JsonRpcRequest req, String sessionId, boolean stream) {
    sessionManager.requireSession(sessionId);

    McpToolCallParams params = parseParams(req.getParams(), McpToolCallParams.class);
    McpToolDescriptor tool = registry.findTool(params.getName())
        .orElseThrow(() -> new McpToolNotFoundException(params.getName()));

    Object callResult = invoker.invoke(tool, params.getArguments());
    McpToolCallResult result = McpToolCallResult.text(callResult.toString());

    if (stream) {
        return sseResponse(req.getId(), result);
    }
    return Response.ok(JsonRpcResponse.success(req.getId(), result)).build();
}
```

### 8.5 Helper SSE

```java
private Response sseResponse(String id, Object result) {
    JsonRpcResponse rpcResponse = JsonRpcResponse.success(id, result);
    String ssePayload = "event: message\ndata: " 
        + toJson(rpcResponse) + "\n\n";

    StreamingOutput stream = out -> {
        out.write(ssePayload.getBytes(StandardCharsets.UTF_8));
        out.flush();
        // Pour v1 : stream immédiatement fermé après la réponse unique
        // Pour v2 (streaming LLM) : garder le stream ouvert + émettre chunks
    };

    return Response.ok(stream, MediaType.SERVER_SENT_EVENTS)
        .header("Cache-Control", "no-cache")
        .header("Connection", "keep-alive")
        .build();
}
```

> **Point clé** : Pour v1, le SSE stream est utilisé en mode "one-shot" (une seule réponse puis fermeture). La vraie valeur du streaming long (tokens LLM) viendra en v2 quand LangChain4j streamera ses `AiMessage`.

---

## 9. Session Manager

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

```java
public class McpSession {
    private final String id;
    private final Instant createdAt;
    private final JsonObject clientCapabilities;
    private volatile boolean initialized = false;
    // ...
}
```

---

## 10. Invocateur — `McpToolInvoker`

```java
@ApplicationScoped
public class McpToolInvoker {

    @Inject BeanManager bm;

    public Object invoke(McpToolDescriptor descriptor, JsonObject arguments) {
        // Récupération du bean CDI via le BeanManager (pas new !)
        Bean<?> bean = bm.resolve(bm.getBeans(descriptor.getBeanType()));
        CreationalContext<?> ctx = bm.createCreationalContext(bean);
        Object instance = bm.getReference(bean, descriptor.getBeanType(), ctx);

        // Conversion des arguments JSON → types Java attendus par la méthode
        Object[] args = resolveArguments(descriptor.getMethod(), arguments);

        try {
            return descriptor.getMethod().invoke(instance, args);
        } catch (InvocationTargetException e) {
            throw new McpToolInvocationException(descriptor.getName(), e.getCause());
        } catch (IllegalAccessException e) {
            throw new McpToolInvocationException(descriptor.getName(), e);
        }
    }

    private Object[] resolveArguments(Method method, JsonObject arguments) {
        // Pour chaque paramètre : lire la valeur JSON, convertir vers le type Java
        // Utiliser JSON-B ou Jakarta JSON pour la conversion
    }
}
```

---

## 11. Gestion des erreurs

Codes d'erreur JSON-RPC 2.0 standard + extensions MCP :

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

Mapper JAX-RS pour les exceptions MCP :
```java
@Provider
public class McpExceptionMapper implements ExceptionMapper<McpException> {
    @Override
    public Response toResponse(McpException e) {
        return Response.ok(JsonRpcResponse.error(e.getRequestId(),
            new JsonRpcError(e.getCode(), e.getMessage())))
            .build();
        // Note : MCP retourne toujours HTTP 200, les erreurs sont dans le payload
    }
}
```

---

## 12. Configuration

Via **MicroProfile Config** si disponible, sinon defaults :

```java
@ApplicationScoped
public class McpServerConfig {

    @Inject
    @ConfigProperty(name = "mcp.server.name", defaultValue = "langchain4j-cdi")
    String serverName;

    @Inject
    @ConfigProperty(name = "mcp.server.version", defaultValue = "1.0.0")
    String serverVersion;

    @Inject
    @ConfigProperty(name = "mcp.server.path", defaultValue = "/mcp")
    String endpointPath;
}
```

> Si MicroProfile Config n'est pas disponible dans le classpath, prévoir un fallback via `ServiceLoader` ou annotation `@McpServerInfo` sur un bean CDI producer.

---

## 13. Stratégie de test

### 13.1 Tests unitaires — `McpToolRegistryTest`
- Tester le scan de beans fictifs avec méthodes `@Tool`
- Tester la génération JSON Schema pour les types scalaires
- Tester la sérialisation/désérialisation des POJOs JSON-RPC

### 13.2 Tests CDI — Weld SE
```java
@ExtendWith(WeldJunit5Extension.class)
@AddBeanClasses({McpToolRegistry.class, McpToolInvoker.class, WeatherTool.class})
class McpToolRegistryTest {
    @Inject McpToolRegistry registry;

    @Test
    void shouldDiscoverWeatherTool() {
        assertThat(registry.listTools()).hasSize(1);
        assertThat(registry.listTools().iterator().next().getName())
            .isEqualTo("getWeather");
    }
}
```

### 13.3 Tests d'intégration — RestAssured
```java
@Test
void shouldRespondToToolsList() {
    // 1. initialize
    String sessionId = given()
        .contentType(ContentType.JSON)
        .body(initializeRequest())
        .post("/mcp")
        .then().statusCode(200)
        .extract().header("Mcp-Session-Id");

    // 2. tools/list
    given()
        .contentType(ContentType.JSON)
        .header("Mcp-Session-Id", sessionId)
        .body(toolsListRequest())
        .post("/mcp")
        .then()
        .statusCode(200)
        .body("result.tools[0].name", equalTo("getWeather"));
}
```

### 13.4 Bean de test `WeatherTool`
```java
@ApplicationScoped
public class WeatherTool {

    @Tool("Get the current weather for a given city")
    public String getWeather(
            @P("The city name") String city,
            @P("Unit: celsius or fahrenheit") String unit) {
        return "Sunny, 22°C in " + city;
    }
}
```

---

## 14. Structure de la PR

### Commits suggérés (un commit par couche)

```
feat(mcp-server): add module skeleton and Maven POM
feat(mcp-server): add JSON-RPC 2.0 protocol POJOs
feat(mcp-server): add McpServerExtension CDI Extension (tool scanning)
feat(mcp-server): add McpToolRegistry and McpToolInvoker
feat(mcp-server): add JsonSchemaGenerator (scalar types)
feat(mcp-server): add McpEndpoint JAX-RS (initialize + tools/list)
feat(mcp-server): add tools/call dispatch and invocation
feat(mcp-server): add SSE response support (Streamable HTTP)
feat(mcp-server): add McpSessionManager
feat(mcp-server): add error handling and ExceptionMapper
feat(mcp-server): add MicroProfile Config integration
test(mcp-server): add unit tests (registry, schema generator)
test(mcp-server): add integration tests (RestAssured + Weld SE)
docs(mcp-server): add README with usage examples
```

### Checklist PR
- [ ] Aucune dépendance compile vers Spring / Quarkus / Reactor
- [ ] `mvn dependency:analyze` ne remonte pas de dépendances Spring transitives
- [ ] `tools/list` retourne un JSON Schema valide (validé par json-schema-validator en test)
- [ ] Le handshake `initialize` / `tools/list` / `tools/call` passe en intégration
- [ ] SSE fonctionne avec `Accept: text/event-stream`
- [ ] `Mcp-Session-Id` absent retourne une erreur JSON-RPC propre (pas HTTP 4xx)
- [ ] Compatible Java 11+ (pas de preview features)
- [ ] JavaDoc sur les classes publiques de l'API

---

## 15. Points d'attention / risques

| Risque | Mitigation |
|--------|-----------|
| JAX-RS SSE sur POST non standard | `StreamingOutput` + headers manuels, testé sur WildFly/TomEE/Payara |
| `BeanManager.getReference()` scope CDI incorrect | Utiliser `@Dependent` pour `McpToolInvoker` ou gérer le `CreationalContext` |
| JSON Schema incomplet pour types complexes | Documenter la limite v1, prévoir SPI `JsonSchemaContributor` pour v2 |
| Concurrence sur `McpSessionManager` | `ConcurrentHashMap` + session TTL (cleanup via `@Scheduled` ou lazy) |
| MicroProfile Config absent du classpath | Fallback CDI producer avec `@Default` sur les valeurs de config |
| Méthode `@Tool` avec return type `void` | Retourner `McpToolCallResult.empty()` |

---

## 16. Évolutions post-v1 (backlog)

- `GET /mcp` — stream SSE server-initiated pour notifications
- `resources/list` + `resources/read` — annoter des CDI producers `@McpResource`
- `prompts/list` + `prompts/get` — annoter des méthodes `@McpPrompt`
- Streaming long des réponses LLM via SSE chunks
- `JsonSchemaContributor` SPI pour types complexes
- Intégration avec OpenTelemetry pour traçage des `tools/call`