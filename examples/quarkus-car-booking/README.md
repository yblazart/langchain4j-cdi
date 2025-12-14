# Miles of Smiles - Quarkus Example

A demonstration of an AI-powered car rental assistant using **langchain4j-cdi** on Quarkus.

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
./runexample.sh
```

This script will:
1. Start Ollama (locally or via Docker/Podman)
2. Pull the llama3.1 model if needed
3. Start Quarkus in dev mode

Stop with `Ctrl+C` when done.

## Using the Demo

### Web Interface

Open your browser and navigate to:

```
http://localhost:8080/
```

You will have access to the Quarkus Dev UI and the chat interface.

### REST API

You can also interact with the assistant via the REST API:

```bash
curl -X GET 'http://localhost:8080/api/car-booking/chat?question=Hello'
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

---

Powered by [langchain4j-cdi](https://github.com/langchain4j/langchain4j-cdi)
