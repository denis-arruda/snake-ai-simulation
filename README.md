# Distributed Multi-Agent Snake Simulation

A distributed multi-agent system where independent AI agents control snakes competing in a shared 30×30 grid. Built to demonstrate event-driven architecture, asynchronous decision-making, fault tolerance, and observability in a real-time system.

This project is an independent implementation of a classic Snake-style game, focused on distributed multi-agent systems and observability.

See [docs/spec.md](docs/spec.md) for the full technical specification.

---

## How it works

- The **Game Engine** runs a tick-based game loop and publishes state snapshots to Apache Kafka
- Each **Agent** consumes its own perception window, calls an LLM (OpenAI) to decide the next move, and publishes its decision back
- The engine applies the latest known decision each tick — agents that are slow or down simply continue in their last direction
- A **WebSocket** endpoint streams the render state to the browser in real time
- All traces, metrics, and logs flow to the **Grafana LGTM** stack via OpenTelemetry

---

## Requirements

- **Docker** and **Docker Compose**
- **Java 26** (for building images)
- **Maven** — or use the included wrapper `./mvnw`
- An **OpenAI API key** (for the AI agents)

---

## Getting started

### 1. Set your API key

```bash
export LLM_API_KEY=your-openai-api-key
```

Optionally override the model (default: `gpt-4o-mini`):

```bash
export AGENT_LLM_MODEL=gpt-4o-mini
```

### 2. Build the container images

```bash
./mvnw package -Dquarkus.container-image.build=true -DskipTests
```

### 3. Start all services

```bash
docker compose up
```

---

## Ports

| Service | URL |
|---|---|
| Game Engine (HTTP + WebSocket) | http://localhost:8081 |
| WebSocket endpoint | ws://localhost:8081/ws/game |
| Grafana (dashboards) | http://localhost:3000 |
| OTLP gRPC (traces / metrics / logs) | localhost:4317 |
| OTLP HTTP | localhost:4318 |
| Kafka broker | localhost:9092 |

---

## Project structure

```
├── common/       Shared message model (AgentState, AgentDecision, RenderState)
├── engine/       Quarkus app — game loop, WebSocket gateway, Kafka producer/consumer
├── agent/        Quarkus app — LLM-driven snake controller
├── docs/
│   └── spec.md   Full technical specification
└── docker-compose.yml
```
