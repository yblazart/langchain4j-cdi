# Miles of Smiles - Helidon Example (Build-Compatible Extension)

A demonstration of an AI-powered car rental assistant using **langchain4j-cdi** on Helidon 4 with the build-compatible CDI extension.

## About

This example is based on a simplified car booking application inspired from the [Java meets AI](https://www.youtube.com/watch?v=BD1MSLbs9KE) talk from [Lize Raes](https://www.linkedin.com/in/lize-raes-a8a34110/) at Devoxx Belgium 2023. The car booking company is called "Miles of Smiles" and the application exposes two AI services:

- A **chat service** to freely discuss with a customer assistant (with RAG support)
- A **fraud service** to determine if a customer is a fraudster

## Prerequisites

- Java 21+
- Maven 3.9+
- Ollama running locally (or Docker/Podman)

## Running the Demo

Start the application with:

```bash
./run.sh
```

This script will:
1. Start Ollama (locally or via Docker/Podman)
2. Pull the llama3.1 model if needed
3. Build and start the Helidon application

Stop with `Ctrl+C` when done.

## Using the Demo

### OpenAPI UI

Open your browser and navigate to:

```
http://localhost:8080/openapi/ui
```

You can interact with the REST API through the OpenAPI UI.

### REST API

```bash
# Chat service
curl -X GET 'http://localhost:8080/api/car-booking/chat?question=Hello'

# Fraud detection
curl -X GET 'http://localhost:8080/api/car-booking/fraud?name=James%20Bond'
```

## Sample Questions

Try asking:

- "Hello, how can you help me?"
- "What is your cancellation policy?"
- "What is your list of cars?"
- "My name is James Bond, please list my bookings"
- "Is my booking 123-456 cancelable?"

For fraud detection:
- James Bond
- Emilio Largo

## Configuration

All configuration is centralized in `microprofile-config.properties` and can be redefined using environment variables.

## Packaging

To package the application:

```bash
mvn package
```

---

Powered by [langchain4j-cdi](https://github.com/langchain4j/langchain4j-cdi)
