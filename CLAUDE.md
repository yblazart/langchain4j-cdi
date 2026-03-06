# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LangChain4j CDI is an enterprise CDI extension for LangChain4j. It enables injecting AI services (`@RegisterAIService`) into Jakarta EE and MicroProfile applications. Supports portable extensions (WildFly, Payara, GlassFish, Liberty) and build-compatible extensions (Quarkus, Helidon).

## Build Commands

**Prerequisites**: Java 17+ (JDK 21+ required for integration tests), Maven via `./mvnw` wrapper.

Always exclude Payara example (`-pl '!examples/payara-car-booking'`) — it fails due to network issues.

```bash
# Build (skip tests)
./mvnw -B clean install -DskipTests -pl '!examples/payara-car-booking'

# Run unit tests (exclude integration tests)
./mvnw -B clean test -pl '!examples/payara-car-booking,!langchain4j-cdi-integration-tests'

# Run all tests including integration
./mvnw -B clean test -pl '!examples/payara-car-booking'

# Run a single test class
./mvnw -B test -pl langchain4j-cdi-core -Dtest=CommonLLMPluginCreatorTest

# Run a single test method
./mvnw -B test -pl langchain4j-cdi-core -Dtest=CommonLLMPluginCreatorTest#testBeanCreation

# Lint (check formatting)
./mvnw -B spotless:check -pl '!examples/payara-car-booking'

# Auto-fix formatting
./mvnw -B spotless:apply -pl '!examples/payara-car-booking'

# Full CI validation
./mvnw -B spotless:check verify -pl '!examples/payara-car-booking'
```

**Note**: CONTRIBUTING.md mentions `make lint` / `make format` — these don't exist. Use `spotless:check` / `spotless:apply`.

## Code Style

- **Formatter**: Spotless with Palantir Java Format (style: PALANTIR)
- **Java version**: 17 target, avoid Lombok
- **Backward compatibility**: Don't remove public API — deprecate instead

## Architecture

### Module Structure

```
langchain4j-cdi-core/              # Core: annotations, SPI, AI service creation logic
langchain4j-cdi-portable-ext/      # CDI Portable Extension (runtime, for most app servers)
langchain4j-cdi-build-compatible-ext/ # CDI Build-Compatible Extension (Quarkus, Helidon)
langchain4j-cdi-mp/                # MicroProfile integrations parent
  langchain4j-cdi-config/          #   MP Config adapter for LLMConfig SPI
  langchain4j-cdi-fault-tolerance/ #   MP Fault Tolerance support
  langchain4j-cdi-telemetry/       #   OpenTelemetry metrics
langchain4j-cdi-integration-tests/ # Integration tests (Quarkus, Helidon, Jakarta EE servers)
examples/                          # Example apps per runtime
```

### How AI Services Are Registered as CDI Beans

1. `@RegisterAIService` annotation marks an interface as an AI service
2. CDI extensions detect annotated interfaces:
   - **Portable**: `LangChain4JAIServicePortableExtension` observes `ProcessAnnotatedType`, then registers synthetic beans in `AfterBeanDiscovery`
   - **Build-compatible**: `Langchain4JAIServiceBuildCompatibleExtension` processes `ClassConfig` during enhancement, then creates synthetic beans during synthesis
3. `CommonAIServiceCreator.create()` builds the LangChain4j `AiServices` instance at runtime, resolving ChatModel, tools, memory, RAG components from CDI by name

### Configuration System (LLMConfig SPI)

Property-based bean creation without CDI producers:

- `LLMConfig` (abstract class) is loaded via `ServiceLoader` through `LLMConfigProvider`
- Default implementation: `LLMConfigMPConfig` in `langchain4j-cdi-config` (uses MicroProfile Config)
- `CommonLLMPluginCreator` reads config, discovers Builder inner classes via reflection, maps dash-case properties to camelCase fields/methods
- Property format: `dev.langchain4j.cdi.plugin.<bean-name>.class=...`, `...config.<property>=<value>`
- `lookup:` prefix in values triggers CDI bean resolution (`@default`, `@all`, or named)

### Key Source Files

- `langchain4j-cdi-core/.../RegisterAIService.java` — Main annotation
- `langchain4j-cdi-core/.../CommonAIServiceCreator.java` — AiServices factory
- `langchain4j-cdi-core/.../CommonLLMPluginCreator.java` — Reflection-based bean builder from config
- `langchain4j-cdi-core/.../config/spi/LLMConfig.java` — Configuration SPI
- `langchain4j-cdi-portable-ext/.../LangChain4JAIServicePortableExtension.java` — Portable extension
- `langchain4j-cdi-build-compatible-ext/.../Langchain4JAIServiceBuildCompatibleExtension.java` — Build-compatible extension

### Testing

- **Unit tests**: JUnit 5 + Mockito + AssertJ, Weld for CDI container in tests (`weld-junit5`)
- **Integration tests**: Arquillian (Jakarta EE), `@QuarkusTest` (Quarkus), Helidon testing
- Quarkus example is recommended for quick iteration (`./mvnw quarkus:dev` in `examples/quarkus-car-booking/`)
