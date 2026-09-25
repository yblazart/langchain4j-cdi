---
title: MCP Server
description: Turn CDI beans into Model Context Protocol servers exposing tools, prompts, and resources.
layout: page
---

# MCP Server

The `langchain4j-cdi-mcp` module tree turns CDI beans into a **Model Context Protocol (MCP) server**. It exposes annotated bean methods as MCP **tools**, **prompts**, and **resources** over JSON-RPC 2.0 / Streamable HTTP at the `/mcp` endpoint, on any CDI runtime: Quarkus, Helidon, WildFly, Payara, GlassFish, Liberty.

Built on the [MCP Java](https://github.com/mcp-java) project (`java-mcp-annotations`). This page is an overview; the [module README](https://github.com/langchain4j/langchain4j-cdi/blob/main/langchain4j-cdi-mcp/README.md) is the full reference.

## Quick Setup

Add the server and the CDI extension that matches your runtime.

**WildFly / Payara / GlassFish / Liberty** (portable extension):

```xml
<dependency>
    <groupId>dev.langchain4j.cdi.mcp</groupId>
    <artifactId>langchain4j-cdi-mcp-server</artifactId>
    <version>$\{langchain4j-cdi-mcp.version}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j.cdi.mcp</groupId>
    <artifactId>langchain4j-cdi-mcp-portable-ext</artifactId>
    <version>$\{langchain4j-cdi-mcp.version}</version>
</dependency>
```

**Quarkus / Helidon** (build-compatible extension): the same, with `langchain4j-cdi-mcp-build-compatible-ext` instead of `langchain4j-cdi-mcp-portable-ext`.

The MCP module tree has its own group id and version, independent of the rest of LangChain4j CDI.

## Tools, Prompts and Resources

```java
@ApplicationScoped
public class WeatherService {

    @Tool(description = "Get the current weather for a city")
    public String getWeather(
            @ToolArg(description = "City name") String city,
            @ToolArg(description = "Unit: celsius or fahrenheit", required = false) String unit) {
        return "Sunny, 22°C in " + city;
    }

    @Prompt(description = "Summarize the given text concisely")
    public String summarize(@PromptArg(description = "The text to summarize") String text) {
        return "Please summarize the following text in 3 bullet points:\n\n" + text;
    }

    @Resource(uri = "data://config", description = "Application configuration")
    public String config() {
        return loadConfiguration();
    }
}
```

The JSON Schema of each tool is generated from its Java signature. Arguments are bound by their declared type:
- an `Optional` parameter, or one with a default value, is not required;
- enums are listed by `name()`;
- an argument whose JSON type contradicts the schema is rejected with a protocol error, never a 500.

Tools also accept titles, annotations, icons, custom headers (SEP-2243) and a hand-written input schema (`@McpInputSchema`).

## Framework Types

These parameters are injected at invocation time and excluded from the JSON Schema:

| Type | Description |
|------|-------------|
| `McpLog` | Structured logging within tool execution |
| `Progress` | Report progress to the client |
| `Cancellation` | Check if the client cancelled the request |
| `McpConnection` | Access to the MCP connection |
| `Roots` | Access client-provided root URIs |
| `Sampling` | Request LLM sampling from the client |
| `Elicitation` | Request user input from the client |
| `McpInteractions` | Declare several client interactions as one batch and await all their answers together |

## Two Protocol Eras on One Endpoint

The same `/mcp` endpoint serves MCP **2025-03-26**, with `initialize`, the `Mcp-Session-Id` header and a GET SSE stream. It also serves MCP **2026-07-28**, which is stateless: per-request `_meta`, `server/discover` and `subscriptions/listen`. Each request is routed to the right era automatically.

With 2026-07-28 clients the server cannot send its own requests. An elicitation, a sampling or a roots request becomes a *multi round-trip request* (MRTR): the server answers `input_required`, and the client retries with the answers. Two strategies are available:

- **`REPLAY`** (default): the method runs again from the start on each retry, and the answers already given travel in an encrypted, expiring `requestState`. It is stateless and works behind any load balancer.
- **`CONTINUATION`**: the method waits on a worker thread between rounds. It needs sticky routing.

A tool can choose the key of each interaction (SEP-2322), and batch several interactions so that a client sees every missing request in one round.

## Configuration

Produce an optional `McpServerConfig` bean named `mcp-server`. The server reads no configuration property of its own.

```java
@Produces
@Named("mcp-server")
@ApplicationScoped
McpServerConfig mcpServerConfig() {
    return McpServerConfig.builder()
            .serverName("car-booking")
            .allowedOrigins(List.of("https://app.example.com"))
            .requestStateSecret(System.getenv("MCP_REQUEST_STATE_SECRET")) // >= 32 characters
            .build();
}
```

| Property | Default | Description |
|---|---|---|
| `serverName` / `serverVersion` | `langchain4j-cdi` / `unknown` | Server identity |
| `allowedOrigins` | loopback only | Accepted `Origin` values; `*` accepts all |
| `mrtrMode` | `REPLAY` | `REPLAY` or `CONTINUATION` |
| `requestStateSecret` | random per JVM | At least 32 characters; set the same value on every instance of a cluster |
| `requestStateTtl` | 10 minutes | Validity of a `requestState` |
| `continuationTimeout` | 5 minutes | `CONTINUATION` mode: maximum wait per round |
| `maxSessions` | 1000 | Concurrent 2025-03-26 sessions; zero or negative means unlimited |
| `maxContinuations` | 200 | Concurrent `CONTINUATION` calls; zero or negative means unlimited |
| `cacheTtl` / `cacheScope` | 0 / `public` | SEP-2549 cache hints on cacheable results |

## Security of the Request State

The `requestState` is **encrypted and authenticated** with AES-256-GCM. A client, a proxy or an access log holding it can neither read the elicitation and sampling answers it carries nor alter them; the tool name and arguments travel in clear beside it, in the request itself. It is bound to the call that issued it, and it expires after `requestStateTtl`.

The AES key is derived from `requestStateSecret`. The secret is never logged. Without a secret, the server draws a random secret per JVM, which only works for a single instance. Use a random value (`openssl rand -base64 32`), not a passphrase, and rotate it from time to time.

## Reflection-Free Invocation

The optional `langchain4j-cdi-mcp-invoker-cdi41` module invokes the bean methods through the CDI 4.1 invoker API instead of reflection. It is verified on Quarkus. CDI 4.1 is a hard prerequisite: on a CDI 4.0 runtime, it breaks the deployment.

## Architecture

- **`McpEndpoint`**: the JAX-RS `/mcp` resource. It routes each request to the handler of its protocol era.
- **`McpSessionManager`**: 2025-03-26 sessions, which expire after 30 minutes idle.
- **`McpNotificationBroadcaster`**: SSE notifications to connected clients.
- **`JsonSchemaGenerator`**: JSON Schema generation from Java method signatures.
