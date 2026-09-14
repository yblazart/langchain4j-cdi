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
| Errors | HTTP 200 + JSON-RPC error | HTTP 400 for `HeaderMismatch` (-32020), `MissingRequiredClientCapability` (-32021), `UnsupportedProtocolVersion` (-32022); HTTP 404 for unknown methods |

`server/discover` advertises `supportedVersions: ["2026-07-28", "2025-03-26"]`. A modern request with another version is rejected with `UnsupportedProtocolVersion` listing these versions, and dual-era clients (such as `langchain4j-mcp` 1.19+) fall back automatically.

The server validates the `Origin` header on every request (DNS rebinding protection): requests without `Origin`, from loopback origins or from the same host are accepted; other origins get HTTP 403 unless listed in `allowedOrigins` (see [Server Configuration](#server-configuration)).

#### Known limitations

- **Request-scoped notifications may be batched on some runtimes.** Progress (`Progress`) and log (`McpLog`) notifications emitted during a modern `tools/call`, `prompts/get` or `resources/read` streamed reply are sent on that request's SSE stream, but Helidon (Jersey output buffering, ~8 KB) and Open Liberty (~32 KB) may hold them until the final result is written. `subscriptions/listen` streams and the legacy `GET /mcp` stream use the Jakarta REST `SseEventSink` API and are delivered immediately on every runtime.
- **Quarkus SSE framing/headers on sink streams.** On Quarkus, the `subscriptions/listen` and legacy `GET /mcp` streams are written as `data:{...}` (no space, valid SSE) and do not carry the `Cache-Control`, `X-Accel-Buffering` and `Mcp-Session-Id` response headers. If a reverse proxy buffers SSE, configure it to disable buffering for `/mcp`.
- **Deprecated spec features still supported.** MCP 2026-07-28 deprecates Roots, Sampling and Logging: they keep working but new servers should prefer tool arguments, direct LLM calls and OpenTelemetry.

### Client Interactions with MCP 2026-07-28

`Elicitation`, `Sampling` and `Roots` keep the same blocking Java API (`sendAndAwait()`, `listAndAwait()`) in both eras. With modern clients, the server cannot send its own requests: it answers `input_required`, and the client retries the call with the answers (*multi round-trip requests*). Two strategies are available through `McpServerConfig.mrtrMode`:

| Mode | How it works | Constraints |
|---|---|---|
| `REPLAY` (default) | The method is **re-executed from the beginning** on each retry. Answers already given are replayed in call order; they travel in a signed, expiring `requestState`. | Code executed before an interaction must be idempotent, and interactions must happen in a deterministic order. Stateless: works behind any load balancer if all instances share `requestStateSecret`. |
| `CONTINUATION` | The first call starts the method on a worker thread which waits for the answer; retries resume it. | In-memory state: requires sticky routing in a cluster. `@RequestScoped` beans are not active on the worker thread. Each round must complete within `continuationTimeout`. A round that exceeds `continuationTimeout` fails the request, but a compute-bound method is not interrupted (it keeps its worker thread until it returns). Worker threads come from an unbounded cached pool: size `continuationTimeout` accordingly and prefer `REPLAY` for public-facing servers. The log-level threshold of the first round applies to all rounds (progress tokens, cancellation and whether logs are sent at all follow each round). |

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
                .build();
    }
}
```

| Property | Default | Description |
|---|---|---|
| `serverName` / `serverVersion` | `langchain4j-cdi` / `unknown` | Returned in `initialize` and in `_meta.io.modelcontextprotocol/serverInfo` |
| `allowedOrigins` | empty (loopback and same host only) | Accepted `Origin` values; `*` accepts all |
| `mrtrMode` | `REPLAY` | Strategy for client interactions with MCP 2026-07-28 clients |
| `requestStateSecret` | random per JVM | HMAC key protecting `requestState`; **set it when running several instances** |
| `requestStateTtl` | 10 minutes | Validity of a `requestState` |
| `continuationTimeout` | 5 minutes | `CONTINUATION` mode: maximum wait for a client answer or for the method to finish |

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
