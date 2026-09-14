<p align="center">
  <img src="langchain4j-cdi-mcp-server-logo.png" alt="LangChain4j CDI MCP Server Logo" width="400">
</p>

# LangChain4j CDI — MCP Server

Turn any CDI bean into a [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server with a few annotations. The server exposes **tools**, **prompts**, and **resources** over HTTP using the Streamable HTTP transport, so any MCP-compatible client (Claude Desktop, VS Code Copilot, custom agents…) can connect to your Jakarta EE / MicroProfile application.

This module builds on the [MCP Java](https://github.com/mcp-java) project, using [java-mcp-annotations](https://github.com/mcp-java/java-mcp-annotations) for the annotation API (`@Tool`, `@Prompt`, `@Resource`, …).

The server is **dual-era**: on the same `/mcp` endpoint it speaks both MCP **2025-03-26** (`initialize` handshake, `Mcp-Session-Id` sessions) and the stateless MCP **2026-07-28** revision (per-request `_meta`, `server/discover`, multi round-trip requests). Each request is routed according to the protocol version it declares, so existing clients keep working while modern clients get the new protocol.

## Table of Contents

- [How It Works](#how-it-works)
- [Getting Started](#getting-started)
  - [1. Add Dependencies](#1-add-dependencies)
  - [2. Write Your First Tool](#2-write-your-first-tool)
  - [3. Run and Connect](#3-run-and-connect)
- [Features](#features)
  - [Tools](#tools)
  - [Prompts](#prompts)
  - [Resources](#resources)
  - [Framework Types (Logging, Progress, Cancellation…)](#framework-types)
  - [Protocol Versions & Compatibility](#protocol-versions--compatibility)
  - [Client Interactions with MCP 2026-07-28](#client-interactions-with-mcp-2026-07-28)
  - [Server Configuration](#server-configuration)
- [Conformance](#conformance)
- [Runtime Support](#runtime-support)
- [Module Structure](#module-structure)

---

## How It Works

```
┌──────────────┐        JSON-RPC / HTTP         ┌──────────────────────┐
│  MCP Client  │  ───────────────────────────▶   │  Your Jakarta EE App │
│  (Claude,    │                                 │                      │
│   VS Code…)  │  ◀───────────────────────────   │   @Tool, @Prompt,    │
│              │        SSE notifications        │   @Resource beans    │
└──────────────┘                                 └──────────────────────┘
```

1. You annotate CDI bean methods with `@Tool`, `@Prompt`, or `@Resource`.
2. At deployment time, a CDI extension discovers annotated beans and registers them.
3. A JAX-RS endpoint (`/mcp`) is exposed automatically — it speaks the MCP protocol (JSON-RPC 2.0 over Streamable HTTP).
4. Any MCP client can connect and discover/call your tools, read your resources, and get your prompts.

#### Request Routing

Every request lands on the same `/mcp` endpoint and passes through a fixed pipeline before your bean's code runs: the `Origin` header is checked first, then the body must parse as JSON-RPC, and an id-less, session-less request carrying a modern `MCP-Protocol-Version` header is answered `202` immediately without reaching either era-specific handler. From there, `McpEraDetector` decides whether the legacy or the modern handler processes the request; both delegate the actual tool, prompt, resource and completion execution to the same `McpFeatureService`, which is why a feature behaves identically in both eras.

```mermaid
flowchart TD
    A["POST /mcp"] --> B{"Origin valid?"}
    B -- "no" --> B1["403 Forbidden"]
    B -- "yes" --> C{"JSON-RPC parses?"}
    C -- "no" --> C1["400 / -32700 ParseError"]
    C -- "yes" --> D{"no id, no session id, modern header?"}
    D -- "yes" --> D1["202 Accepted"]
    D -- "no" --> E["McpEraDetector"]
    E -- "legacy" --> F["McpLegacyProtocolHandler"]
    E -- "modern" --> G["McpModernProtocolHandler"]
    F --> H["McpFeatureService: tools / prompts / resources / completion"]
    G --> H
```

---

## Getting Started

### 1. Add Dependencies

Pick the extension that matches your runtime:

**WildFly, Payara, GlassFish, Liberty** (portable extension):

```xml
<dependency>
    <groupId>dev.langchain4j.cdi.mcp</groupId>
    <artifactId>langchain4j-cdi-mcp-server</artifactId>
    <version>1.0.0-Beta1</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j.cdi.mcp</groupId>
    <artifactId>langchain4j-cdi-mcp-portable-ext</artifactId>
    <version>1.0.0-Beta1</version>
</dependency>
```

**Quarkus, Helidon** (build-compatible extension):

```xml
<dependency>
    <groupId>dev.langchain4j.cdi.mcp</groupId>
    <artifactId>langchain4j-cdi-mcp-server</artifactId>
    <version>1.0.0-Beta1</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j.cdi.mcp</groupId>
    <artifactId>langchain4j-cdi-mcp-build-compatible-ext</artifactId>
    <version>1.0.0-Beta1</version>
</dependency>
```

> The `mcp-annotations` and `mcp-server-api` transitive dependencies are pulled in automatically.

### 2. Write Your First Tool

Create a CDI bean and annotate a method with `@Tool`:

```java
import jakarta.enterprise.context.ApplicationScoped;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;

@ApplicationScoped
public class WeatherService {

    @Tool(description = "Get the current weather for a city")
    public String getWeather(
            @ToolArg(description = "City name") String city,
            @ToolArg(description = "Unit: celsius or fahrenheit", required = false) String unit) {
        // your real implementation here
        return "Sunny, 22°C in " + city;
    }
}
```

That's it — the method is now callable by any MCP client.

### 3. Run and Connect

Start your application as usual. The MCP endpoint is available at:

```
http://localhost:8080/mcp
```

> The exact URL depends on your application's context root. For example, on WildFly with context root `/my-app`, the URL would be `http://localhost:8080/my-app/mcp`.

To test with `curl` using MCP 2026-07-28 (no session needed):

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "MCP-Protocol-Version: 2026-07-28" \
  -H "Mcp-Method: tools/list" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{
        "io.modelcontextprotocol/protocolVersion":"2026-07-28",
        "io.modelcontextprotocol/clientInfo":{"name":"curl","version":"1.0"},
        "io.modelcontextprotocol/clientCapabilities":{}}}}'
```

Or with the legacy 2025-03-26 handshake:

```bash
# Initialize a session
curl -s -D - -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'

# List available tools (use the Mcp-Session-Id from the previous response)
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

---

## Features

### Tools

Tools are methods that an MCP client can call. Think of them as functions exposed to an AI model.

```java
@ApplicationScoped
public class Calculator {

    @Tool(description = "Add two numbers")
    public int add(
            @ToolArg(description = "First number") int a,
            @ToolArg(description = "Second number") int b) {
        return a + b;
    }

    @Tool(description = "Search the company knowledge base")
    public String search(
            @ToolArg(description = "Search query") String query,
            @ToolArg(description = "Max results to return", required = false) Integer limit) {
        // ...
    }
}
```

**Supported parameter types:** `String`, `int`/`Integer`, `long`/`Long`, `double`/`Double`, `float`/`Float`, `boolean`/`Boolean`, enums, arrays, and collections.

**Optional parameters:** Set `required = false` on `@ToolArg` — the parameter will be `null` if the client doesn't provide it.

### Prompts

Prompts are reusable templates that guide how an AI model should respond. They are returned as structured messages.

```java
@ApplicationScoped
public class MyPrompts {

    @Prompt(description = "Summarize the given text concisely")
    public String summarize(
            @PromptArg(description = "The text to summarize") String text) {
        return "Please summarize the following text in 3 bullet points:\n\n" + text;
    }

    @Prompt(description = "Translate text to a target language")
    public String translate(
            @PromptArg(description = "Text to translate") String text,
            @PromptArg(description = "Target language") String language) {
        return "Translate the following to " + language + ":\n\n" + text;
    }
}
```

### Resources

Resources expose read-only data that clients can retrieve. Each resource has a unique URI.

```java
@ApplicationScoped
public class AppResources {

    @Resource(
            uri = "config://app",
            name = "Application Config",
            description = "Current application configuration",
            mimeType = "application/json")
    public String getConfig() {
        return "{\"version\": \"2.1\", \"environment\": \"production\"}";
    }

    @Resource(
            uri = "data://metrics",
            name = "System Metrics",
            description = "Current system health metrics")
    public String getMetrics() {
        return "cpu=45%, memory=72%, uptime=48h";
    }
}
```

Clients can also **subscribe** to resources and receive notifications when the data changes.

A `@ResourceTemplate` serves a whole family of URIs. The `{variable}` expressions of the URI template (RFC 6570 level 1) are matched against the requested URI and bound to the method's parameters by name:

```java
@ResourceTemplate(
        uriTemplate = "user://{userId}/profile",
        name = "User Profile",
        mimeType = "application/json")
public String userProfile(@ResourceTemplateArg(name = "userId") String userId) {
    return repository.profileJson(userId);
}
```

`resources/read` resolves an exactly registered `@Resource` URI first and falls back to the templates; when several templates match, the most specific one wins (fewest variables, then the longest template). A variable in the middle of the template matches one path segment, a variable ending it matches the rest of the URI.

> **Caveat:** a variable that *ends* the template deliberately captures the rest of the URI, slashes included, so `file:///{path}` matches `file:///a/b/c.txt` with `path = "a/b/c.txt"`. Strict RFC 6570 level-1 expansion percent-encodes `/` and would never produce such a URI, so this is a deliberate convenience: a template ending in a variable is greedy, and one that should only match a single segment must be written with something after it (`file:///{name}/content`). Variable values are percent-decoded; a value that is not valid percent-encoding (`100%`, `a%zz`) is passed through unchanged rather than rejected.

> Without `@ResourceTemplateArg(name = …)` — and likewise without `@ToolArg(name = …)` / `@PromptArg(name = …)` — arguments are named after the Java parameter, which requires the module holding your beans to be **compiled with `-parameters`**; otherwise they are advertised and bound as `arg0`, `arg1`, … Set `<maven.compiler.parameters>true</maven.compiler.parameters>` in your application's POM.

### Framework Types

You can inject special MCP framework types as parameters in any `@Tool`, `@Prompt`, or `@Resource` method. These are resolved automatically at invocation time and do **not** appear in the generated JSON Schema.

```java
@Tool(description = "Long-running data import")
public String importData(
        @ToolArg(description = "File path") String path,
        Progress progress,          // report progress to the client
        Cancellation cancellation,  // check if the client cancelled
        McpLog log) {               // send log messages to the client

    log.info("Starting import of " + path);

    for (int i = 0; i < 100; i++) {
        if (cancellation.check().isCancelled()) {
            return "Import cancelled at " + i + "%";
        }
        // ... process chunk ...
        progress.notification(i, 100, "Processing...").send();
    }

    return "Import complete";
}
```

| Type | Description |
|------|-------------|
| `McpLog` | Send log messages (`debug`, `info`, `warning`, `error`) to the client (MCP 2026-07-28: only when the request sets a log level) |
| `Progress` | Report progress for long-running operations |
| `Cancellation` | Check if the client has cancelled the current request |
| `McpConnection` | Access session (legacy) or per-request client information (MCP 2026-07-28) |
| `Roots` | Access the client's file system roots |
| `Sampling` | Request LLM completions from the client |
| `Elicitation` | Request user input from the client |

All types are from the `org.mcpjava.server` package.

### Protocol Versions & Compatibility

| | MCP 2025-03-26 (legacy) | MCP 2026-07-28 (modern) |
|---|---|---|
| Detection | no `_meta.io.modelcontextprotocol/protocolVersion` in the request | `_meta` version + matching `MCP-Protocol-Version` header |
| Handshake | `initialize` → `Mcp-Session-Id` | none; `server/discover` is optional |
| Required headers | `Mcp-Session-Id` | `MCP-Protocol-Version`, `Mcp-Method`, `Mcp-Name` (`tools/call`, `prompts/get`, `resources/read`) |
| Change notifications | `GET /mcp` SSE stream + `resources/subscribe` | `subscriptions/listen` |
| Elicitation / sampling / roots | server-initiated requests on the GET stream | multi round-trip requests (`input_required` results) |
| Logging | `logging/setLevel` (global) | `io.modelcontextprotocol/logLevel` per request |
| Errors | HTTP 200 + JSON-RPC error; HTTP 400 for a request without a session id, HTTP 404 for one carrying an unknown or terminated session id | HTTP 400 for `InvalidParams` (-32602) on a missing or incomplete `_meta`, `HeaderMismatch` (-32020), `MissingRequiredClientCapability` (-32021), `UnsupportedProtocolVersion` (-32022); HTTP 404 for unknown methods |
| Notification POSTs | HTTP 202 Accepted, empty body | HTTP 202 Accepted, empty body |
| Caching hints | none | `ttlMs` / `cacheScope` (SEP-2549) on `server/discover` and on every cacheable result (`tools/list`, `prompts/list`, `resources/list`, `resources/templates/list`, `resources/read`) |

#### Era Detection

`McpEraDetector` classifies each request by looking for a `protocolVersion` field inside `_meta.io.modelcontextprotocol`. Its absence sends the request to the legacy 2025-03-26 era, unless the `MCP-Protocol-Version` header itself claims a modern version — then the missing `_meta` is malformed input, not a fallback trigger, and the server answers `400` / `-32602` (SEP-2575). When `_meta.protocolVersion` is present, the checks run in a fixed order: the header must agree with it first (`-32020` on any mismatch), then the version must be one the server supports (`-32022`), then the `Mcp-Method` and `Mcp-Name` headers must match the request (`-32020`), and only last is `_meta.clientCapabilities` required (`-32602`) before the request reaches the modern handler. A modern request naming a legacy-only method (`initialize`, `logging/setLevel`, …) is rejected too, but only once dispatch reaches the method switch, which is why it surfaces as `404` / `-32601` rather than a header-level error.

```mermaid
flowchart TD
    A["Request past Origin + JSON-RPC checks"] --> B{"_meta protocolVersion present?"}
    B -- "no" --> B1{"MCP-Protocol-Version header is modern?"}
    B1 -- "yes" --> B2["400 / -32602 InvalidParams: missing _meta, SEP-2575"]
    B1 -- "no" --> B3["Legacy era: 2025-03-26"]
    B -- "yes" --> C{"header matches _meta version?"}
    C -- "no" --> C1["400 / -32020 HeaderMismatch"]
    C -- "yes" --> D{"version is supported?"}
    D -- "no" --> D1["400 / -32022 UnsupportedProtocolVersion"]
    D -- "yes" --> E{"Mcp-Method header matches?"}
    E -- "no" --> C1
    E -- "yes" --> F{"Mcp-Name header matches, if required?"}
    F -- "no" --> C1
    F -- "yes" --> G{"_meta.clientCapabilities present?"}
    G -- "no" --> G1["400 / -32602 InvalidParams: missing clientCapabilities"]
    G -- "yes" --> H["Modern era: 2026-07-28"]
    H --> I{"method is legacy-only?"}
    I -- "yes" --> I1["404 / -32601 MethodNotFound"]
    I -- "no" --> J["dispatch to McpModernProtocolHandler"]
```

`server/discover` advertises `supportedVersions: ["2026-07-28", "2025-03-26"]`. A modern request with another version is rejected with `UnsupportedProtocolVersion` listing these versions, and dual-era clients (such as `langchain4j-mcp` 1.19+) fall back automatically.

The server validates the `Origin` header on every request (DNS rebinding protection). Requests without `Origin` (non-browser clients) are accepted. With the default empty `allowedOrigins`, a request with an `Origin` is accepted only when both the `Origin` host and the `Host` header host are loopback (`localhost`, `127.0.0.1`, `::1`); any other origin — including one matching the `Host` header, which is exactly what a DNS rebinding attack sends — gets HTTP 403. When the server is reached from browsers through a real host name, list the accepted origins in `allowedOrigins` (see [Server Configuration](#server-configuration)).

> **Breaking change:** `McpEndpoint` is container-managed. Its public constructor and its resource method signatures (`handlePost`, `handleGet`, `handleDelete`) changed: code that subclassed `McpEndpoint` or invoked these methods directly must be updated.

> **Breaking change — legacy-era HTTP status codes.** Two legacy (2025-03-26) responses changed to the status the Streamable HTTP transport requires, and clients that hard-coded the old ones must be updated:
>
> - A `POST /mcp` whose body is only JSON-RPC **notifications or responses** now answers **202 Accepted** with an empty body instead of 200. This is not cosmetic: the MCP TypeScript SDK opens the standalone `GET /mcp` notification stream only when `notifications/initialized` is answered exactly 202, so with 200 no server-initiated elicitation, sampling, log or progress message ever reached such a client.
> - A request carrying an unknown or **terminated** session id now answers **404 Not Found** (still with the `-32001` JSON-RPC error body) instead of 200. A request carrying *no* session id stays a 400 Bad Request.

#### Known limitations

Not every server-to-client push travels the same way. The two long-lived streams — `subscriptions/listen` and the legacy `GET /mcp` notification stream — are written through the Jakarta REST `SseEventSink` API, which the runtime flushes after every event, so delivery is immediate everywhere. Request-scoped notifications emitted during a `tools/call`, `prompts/get` or `resources/read` reply instead go through a plain `StreamingOutput`, which some runtimes buffer.

```mermaid
flowchart TD
    A["subscriptions/listen: modern"] --> S["SseEventSink"]
    B["GET /mcp: legacy stream"] --> S
    S --> S1["delivered immediately on every runtime"]
    C["tools/call notifications"] --> O["StreamingOutput"]
    D["prompts/get notifications"] --> O
    E["resources/read notifications"] --> O
    O --> O1["may be batched with the final result: Helidon, Open Liberty"]
```

- **Request-scoped notifications may be batched on some runtimes.** Progress (`Progress`) and log (`McpLog`) notifications emitted during a modern `tools/call`, `prompts/get` or `resources/read` streamed reply go through `StreamingOutput`, and Helidon (Jersey output buffering, ~8 KB) and Open Liberty (~32 KB) may hold them until the final result is written before the client sees any of it.
- **Quarkus SSE framing/headers on sink streams.** On Quarkus, the `subscriptions/listen` and legacy `GET /mcp` streams are written as `data:{...}` (no space, valid SSE) and do not carry the `Cache-Control`, `X-Accel-Buffering` and `Mcp-Session-Id` response headers. If a reverse proxy buffers SSE, configure it to disable buffering for `/mcp`.
- **Deprecated spec features still supported.** MCP 2026-07-28 deprecates Roots, Sampling and Logging: they keep working but new servers should prefer tool arguments, direct LLM calls and OpenTelemetry.

### Client Interactions with MCP 2026-07-28

`Elicitation`, `Sampling` and `Roots` keep the same blocking Java API (`sendAndAwait()`, `listAndAwait()`) in both eras. With modern clients, the server cannot send its own requests: it answers `input_required`, and the client retries the call with the answers (*multi round-trip requests*). Two strategies are available through `McpServerConfig.mrtrMode`:

| Mode | How it works | Constraints |
|---|---|---|
| `REPLAY` (default) | The method is **re-executed from the beginning** on each retry. Answers already given are replayed in call order; they travel in a signed, expiring `requestState`. | Code executed before an interaction must be idempotent, and interactions must happen in a deterministic order. Stateless: works behind any load balancer if all instances share `requestStateSecret`. |
| `CONTINUATION` | The first call starts the method on a worker thread which waits for the answer; retries resume it. | In-memory state: requires sticky routing in a cluster. `@RequestScoped` beans are not active on the worker thread. Each round must complete within `continuationTimeout`. A round that exceeds `continuationTimeout` fails the request, but a compute-bound method is not interrupted (it keeps its worker thread until it returns). Worker threads come from an unbounded cached pool: size `continuationTimeout` accordingly and prefer `REPLAY` for public-facing servers. The log-level threshold of the first round applies to all rounds (progress tokens, cancellation and whether logs are sent at all follow each round). |

#### On The Wire

**`REPLAY`** — the retry is not a continuation of the first call, it is a brand new invocation: the method body starts over at line one, and every previously answered interaction is satisfied from the replayed `requestState` before execution reaches the next one. This is the property to design for: any code before an interaction runs again on every round, so it must be safe to repeat.

```mermaid
sequenceDiagram
    participant Client
    participant Server
    Client->>Server: tools/call, round 1
    Note over Server: method executes from the start
    Server-->>Client: input_required + signed requestState
    Client->>Server: tools/call retry, inputResponses + requestState
    Note over Server: method RE-EXECUTED from the start
    Server->>Server: replay stored answers in call order
    Server->>Server: reach the next unanswered interaction
    Server-->>Client: final result
```

**`CONTINUATION`** — the opposite trade-off: the same worker thread stays parked at the exact point of the interaction, so the retry resumes execution rather than replaying it. Nothing runs twice, but the parked thread and its `@RequestScoped` state must survive until the client answers.

```mermaid
sequenceDiagram
    participant Client
    participant Server
    participant Worker as Worker Thread
    Client->>Server: tools/call, round 1
    Server->>Worker: start method
    Worker->>Worker: park at the interaction, wait for an answer
    Server-->>Client: input_required
    Client->>Server: tools/call retry, inputResponses
    Server->>Worker: resume the SAME parked invocation
    Worker->>Worker: continue past the interaction
    Worker-->>Server: final result
    Server-->>Client: final result
```

```java
@Tool(description = "Book a car after confirming the dates")
public String book(@ToolArg(description = "Car id") String carId, Elicitation elicitation) {
    // REPLAY mode: this line runs again on the retry, keep it side-effect free
    Car car = catalog.find(carId);
    ElicitationResponse answer = elicitation.requestBuilder()
            .setMessage("Rent " + car.name() + "? Enter the start date")
            .addSchemaProperty("startDate", () -> Map.of("type", "string", "format", "date"))
            .build()
            .sendAndAwait();
    if (answer.action() != ElicitationResponse.Action.ACCEPT) {
        return "Booking cancelled";
    }
    // side effects after the last interaction run exactly once
    return bookings.create(car, answer.content().getString("startDate"));
}
```

If the client did not declare the needed capability (`elicitation`, `sampling`, `roots`) in `_meta.io.modelcontextprotocol/clientCapabilities`, the call fails with `MissingRequiredClientCapability`; check `elicitation.isSupported()` first to degrade gracefully. Note that MCP 2026-07-28 deprecates Roots, Sampling and Logging: they keep working but new servers should prefer tool arguments, direct LLM calls and OpenTelemetry.

With modern clients, `McpLog` messages and `Progress` notifications are sent on the SSE response stream of the current request only, and log messages only when the request carried `io.modelcontextprotocol/logLevel`.

### Server Configuration

Provide an optional `McpServerConfig` bean named `mcp-server`:

```java
@ApplicationScoped
public class McpConfigProducer {

    @Produces
    @Named("mcp-server")
    @ApplicationScoped
    McpServerConfig mcpServerConfig() {
        return McpServerConfig.builder()
                .serverName("car-booking")
                .serverVersion("1.0.0")
                .allowedOrigins(List.of("https://app.example.com"))
                .mrtrMode(McpMrtrMode.REPLAY)
                .requestStateSecret(System.getenv("MCP_REQUEST_STATE_SECRET")) // >= 32 characters
                .requestStateTtl(Duration.ofMinutes(10))
                .continuationTimeout(Duration.ofMinutes(5))
                .cacheTtl(Duration.ofMinutes(5))
                .cacheScope("public")
                .build();
    }
}
```

| Property | Default | Description |
|---|---|---|
| `serverName` / `serverVersion` | `langchain4j-cdi` / `unknown` | Returned in `initialize` and in `_meta.io.modelcontextprotocol/serverInfo` |
| `allowedOrigins` | empty (loopback `Origin` on a loopback `Host` only) | Accepted `Origin` values; `*` accepts all |
| `mrtrMode` | `REPLAY` | Strategy for client interactions with MCP 2026-07-28 clients |
| `requestStateSecret` | random per JVM | HMAC key protecting `requestState`; **set it when running several instances** |
| `requestStateTtl` | 10 minutes | Validity of a `requestState` |
| `continuationTimeout` | 5 minutes | `CONTINUATION` mode: maximum wait for a client answer or for the method to finish |
| `cacheTtl` | 0 (immediately stale) | SEP-2549 `ttlMs` emitted on cacheable MCP 2026-07-28 results; raise it when your tool/prompt/resource catalogue is stable |
| `cacheScope` | `public` | SEP-2549 `cacheScope` emitted on the same results; use `private` when a result depends on the caller's authorization context |

---

## Conformance

The server is measured against the official [`@modelcontextprotocol/conformance`](https://github.com/modelcontextprotocol/conformance) suite by the `langchain4j-cdi-mcp-conformance` fixture application (a Helidon MP app serving `/mcp` on port 8080, built with `mvn package`, run with `java -jar target/langchain4j-cdi-mcp-conformance.jar`).

| Run | Score |
|---|---|
| `--spec-version 2026-07-28 --suite all` | **130 passed, 8 failed** |
| `--spec-version 2025-11-25 --suite all` | **74 passed, 1 failed** |
| `@0.1.16` (stable line, default suite) | **40 passed, 0 failed** |

```bash
npx -y @modelcontextprotocol/conformance@alpha server --url http://localhost:8080/mcp \
    --spec-version 2026-07-28 --suite all --expected-failures conformance-baseline.yml
npx -y @modelcontextprotocol/conformance@alpha server --url http://localhost:8080/mcp \
    --spec-version 2025-11-25 --suite all --expected-failures conformance-baseline-legacy.yml
npx -y @modelcontextprotocol/conformance@0.1.16 server --url http://localhost:8080/mcp \
    --expected-failures conformance-baseline-legacy.yml
```

`--suite all` is required: the default `active` suite runs 20 scenarios and silently skips the entire 2026-07-28 surface, including all 15 multi-round-trip (`input-required-result-*`) scenarios.

### Known gaps

The remaining failures are missing features and API limitations, not protocol bugs. They are listed in `conformance-baseline.yml` / `conformance-baseline-legacy.yml`, so a regression anywhere else fails the gate.

- **Custom tool `inputSchema`** — a tool's schema is always derived from its Java signature; there is no API to supply a hand-written JSON Schema 2020-12 document (`$defs`, `allOf`/`anyOf`, `if`/`then`/`else`, …).
- **SEP-2243 `x-mcp-header` / `Mcp-Param-*`** — custom header mirroring is not implemented server-side.
- **SEP-2322 input-request names** — multi-round-trip input requests are named by call order (`input-0`, `input-1`, …); a server cannot choose the key it publishes. Needs an API change on the `Elicitation` / `Sampling` / `Roots` builders.
- **One pending input request per round** — the blocking `sendAndAwait()` API suspends the method at its first interaction, so an `input_required` result always carries a single `inputRequests` entry. Asking several questions in one round trip needs a batch interaction API.
- **Tasks extension** — not implemented.

---

## Runtime Support

| Runtime | Extension Module | How It Works |
|---------|-----------------|--------------|
| **Quarkus** | `langchain4j-cdi-mcp-build-compatible-ext` | Discovers annotations at build time for fast startup |
| **Helidon** | `langchain4j-cdi-mcp-build-compatible-ext` | Same build-compatible extension |
| **WildFly** | `langchain4j-cdi-mcp-portable-ext` | Discovers annotations at deployment time via CDI portable extension |
| **Payara** | `langchain4j-cdi-mcp-portable-ext` | Same portable extension |
| **GlassFish** | `langchain4j-cdi-mcp-portable-ext` | Same portable extension |
| **Liberty** | `langchain4j-cdi-mcp-portable-ext` | Same portable extension |

### Open Liberty / WebSphere Liberty

The MCP endpoint is a **JAX-RS resource** (`@Path("/mcp")`), not a servlet. On Liberty two extra steps are required beyond adding the dependencies — without them the `initialize` request returns **`404 - SRVE0190E: File not found: /mcp`**.

> ⚠️ **Do not confuse this with Liberty's built-in `mcpServer-1.0` feature.** That feature is Liberty's own, unrelated MCP implementation. This module brings its own MCP server over JAX-RS, so `mcpServer-1.0` must **not** be enabled — it is neither used nor required here.

**1. Enable the required features** in `server.xml`:

```xml
<featureManager>
    <feature>restfulWS-3.1</feature> <!-- exposes the /mcp JAX-RS endpoint; pulls in servlet and cdi transitively -->
    <feature>jsonb-3.0</feature>         <!-- used to serialize MCP responses -->
</featureManager>
```

**2. Provide a JAX-RS `Application`** in your application. Liberty does **not** scan `@Path` resources packaged inside `WEB-INF/lib` JARs (where `langchain4j-cdi-mcp-server` lives) unless the application declares an `Application` class, so you must add one:

```java
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/")
public class RestApplication extends Application {}
```

With the context root set to `/`, the endpoint is then reachable at `http://<host>:<port>/mcp`. Use a different `@ApplicationPath` (or context root) to relocate it.

---

## Module Structure

```
langchain4j-cdi-mcp/
├── langchain4j-cdi-mcp-server/              # Core: endpoint, registries, schema generation
├── langchain4j-cdi-mcp-portable-ext/        # CDI Portable Extension (runtime discovery)
├── langchain4j-cdi-mcp-build-compatible-ext/ # CDI Build-Compatible Extension (build-time)
└── langchain4j-cdi-mcp-integration-tests/   # Integration tests
    ├── ...-common/                           # Shared test beans and helpers
    ├── ...-quarkus/                          # Quarkus tests
    ├── ...-helidon/                          # Helidon tests
    ├── ...-wildfly/                          # WildFly Arquillian tests
    └── ...-openliberty/                      # Open Liberty Arquillian tests
```

### Key Components

- **`McpEndpoint`** — JAX-RS resource at `/mcp`: validates `Origin`, detects the protocol era of each request and routes it.
- **`McpEraDetector`** — Reads `_meta` and the `MCP-Protocol-Version`, `Mcp-Method`, `Mcp-Name` headers to classify a request as legacy or modern.
- **`McpLegacyProtocolHandler`** — MCP 2025-03-26: `initialize`, sessions, GET notification stream, server-initiated requests.
- **`McpModernProtocolHandler`** — MCP 2026-07-28: `server/discover`, `resultType`, request-scoped SSE, `subscriptions/listen`, multi round-trip requests.
- **`McpFeatureService`** — Era-independent execution of tools, prompts, resources and completions.
- **`McpToolRegistry` / `McpPromptRegistry` / `McpResourceRegistry`** — Thread-safe registries where discovered beans are stored.
- **`JsonSchemaGenerator`** — Generates JSON Schema from Java method signatures for tool parameter descriptions.
- **`McpSessionManager`** — Manages legacy client sessions with automatic expiration (30 min default).
- **`McpNotificationBroadcaster` / `McpSubscriptionRegistry`** — Deliver change notifications to legacy GET streams and modern `subscriptions/listen` streams.
