# 🐍 Distributed Multi-Agent Snake - Technical Specification

## 1. Overview

This project implements a **distributed multi-agent system** where multiple AI agents control snakes competing in a shared environment.

The goal is to demonstrate:

* Event-driven architecture
* Distributed decision-making
* Resilience to latency and failures
* Observability in multi-agent systems

---

## 2. Architecture Summary

### High-Level Components

* **Game Engine** (includes WebSocket Gateway)
* **Agent Services (2–5 instances)**
* **Messaging Layer (Apache Kafka)**
* **Web UI (Canvas-based)**
* **Observability Stack (Grafana LGTM)**

### Architecture Diagram (Logical)

```
Agents <--> Kafka <--> Game Engine <--> WebSocket <--> Browser
                                |
                          Grafana LGTM
                    (Loki / Tempo / Mimir / Grafana)
```

---

## 3. Functional Requirements

### 3.1 Game Mechanics

* Simulate a **Snake game on a 30×30 grid**
* Each snake is controlled by an independent agent
* Snakes:

  * Move continuously (tick-based)
  * Grow when consuming food
  * Die on collision (wall, self, or other snake)
  * Start with length 3
* Food:

  * Maximum 5 items on the board at any time
  * Replenished immediately when consumed
  * Placed randomly on empty cells

---

### 3.2 Multi-Agent Behavior

* Each snake is controlled by an independent service
* Agents must:

  * Receive a **partial environment state** (7×7 window centered on head)
  * Decide next movement direction
  * Publish decision asynchronously

---

### 3.3 Game Loop

* The engine must:

  * Run at fixed intervals (tick-based)
  * Update game state continuously
  * Apply latest known decision per agent
  * Not block waiting for agent responses

---

### 3.4 Communication

* Communication must be **asynchronous**
* Agents must not receive every game update
* Agents must process **latest available state only**

---

### 3.5 UI Requirements

* Display:

  * Snakes (color-coded per agent)
  * Food
  * Grid
* Real-time updates via WebSocket (served by the engine)
* Optional controls:

  * Start / Stop
  * Speed adjustment

---

## 4. Non-Functional Requirements

### 4.1 Performance

* Engine tick: 100–300 ms
* Agent response time: up to 2 seconds
* UI latency: < 200 ms perceived delay

---

### 4.2 Scalability

* Number of agents configurable (2–5)
* Agents scale independently
* Messaging supports horizontal scaling

---

### 4.3 Resilience

* System must continue if:

  * Agent is slow
  * Agent fails
* Use last known decision as fallback
* Fault tolerance via MicroProfile Fault Tolerance (`quarkus-smallrye-fault-tolerance`):

  | Boundary | Policy | Configuration |
  |---|---|---|
  | Agent → LLM (OpenAI) | `@Timeout` | 12 s safety net above LangChain4j's own 10 s |
  | Agent → LLM (OpenAI) | `@Retry` | 2 retries, 500 ms delay; aborts on timeout |
  | Agent → LLM (OpenAI) | `@CircuitBreaker` | Opens after 60 % failures in 5 requests; stays open 30 s |
  | Agent → LLM (OpenAI) | `@Fallback` | Returns `null` → decision skipped → snake holds direction |
  | Engine → Kafka (perception) | `@Retry` | 2 retries, 100 ms delay |
  | Engine → Kafka (perception) | `@CircuitBreaker` | Opens after 50 % failures in 10 requests; stays open 15 s |
  | Engine tick | `@Timeout` | 1 s — prevents tick pile-up under load |

---

### 4.4 Observability

* **Logs:** structured logs shipped to Loki
* **Metrics:** Micrometer in Prometheus native format, scraped by Prometheus
* **Tracing:** OpenTelemetry traces exported via OTLP to Tempo
* All visualized in Grafana

---

## 5. Technical Stack

| Layer | Technology |
|---|---|
| Language | Java 26 |
| Runtime | Quarkus |
| Concurrency | Virtual threads (`quarkus-virtual-threads`) — all blocking handlers run on virtual threads via `@RunOnVirtualThread` |
| Messaging | Apache Kafka |
| AI / LLM | LangChain4j |
| LLM Model | Configurable via `AGENT_LLM_MODEL` (default: `gpt-4o-mini`) |
| LLM Provider | OpenAI — key via `LLM_API_KEY` env var |
| UI | HTML5 Canvas + JavaScript |
| Transport | WebSocket (embedded in engine) |
| Observability | Grafana LGTM (Loki, Grafana, Tempo, Mimir) |
| Metrics format | Prometheus native (`/q/metrics`) |
| Tracing protocol | OTLP (HTTP or gRPC) |
| Infrastructure | Docker Compose |

---

## 6. Messaging Design (Apache Kafka)

### 6.1 Topics

#### 6.1.1 Agent Perception

```
snake-agent-state-{agentId}
```

* One topic per agent
* Contains filtered state (7×7 visibility window)

---

#### 6.1.2 Agent Decisions

```
snake-agent-decisions
```

* Shared topic
* All agents publish decisions here

---

#### 6.1.3 Render State

```
snake-render-state
```

* Consumed by the WebSocket handler in the engine
* Broadcast to all connected browser clients

---

### 6.2 Message Schemas

#### Agent State

```json
{
  "tick": 120,
  "agentId": "agent-1",
  "self": {
    "head": { "x": 5, "y": 5 },
    "direction": "RIGHT",
    "length": 4,
    "body": [{ "x": 5, "y": 5 }, { "x": 4, "y": 5 }, { "x": 3, "y": 5 }]
  },
  "visibleFood": [{ "x": 6, "y": 5 }],
  "nearbySnakes": [{ "agentId": "agent-2", "x": 8, "y": 7 }],
  "windowSize": 7,
  "gridSize": 30
}
```

---

#### Agent Decision

```json
{
  "agentId": "agent-1",
  "direction": "UP",
  "basedOnTick": 120,
  "decisionTimeMs": 420
}
```

---

#### Render State

```json
{
  "tick": 120,
  "snakes": [
    {
      "agentId": "agent-1",
      "cells": [{ "x": 5, "y": 5 }, { "x": 4, "y": 5 }, { "x": 3, "y": 5 }],
      "direction": "RIGHT",
      "alive": true
    }
  ],
  "foods": [
    { "x": 6, "y": 5 },
    { "x": 12, "y": 18 }
  ]
}
```

---

## 7. Game Engine Design

### Responsibilities

* Maintain global game state (30×30 grid)
* Execute tick-based game loop
* Compute and publish per-agent perception (7×7 window)
* Consume agent decisions
* Serve WebSocket connections and push render-state to browsers

---

### Tick Processing

```
1. Update each snake's position using its last known direction
2. Detect collisions (wall, self, other snake)
3. Handle food consumption (grow snake, replenish food)
4. Publish render state to Kafka (render-state topic)
5. Publish perception snapshots to per-agent Kafka topics (sampled, not every tick)
6. Push render state to connected WebSocket clients
```

---

### Decision Handling

* Store latest decision per agent in memory
* Apply on next tick
* Decisions referencing a tick older than N ticks may be discarded (configurable threshold)

---

## 8. Agent Design

### Responsibilities

* Consume perception messages from its dedicated topic
* Build a prompt from the perception state
* Call LLM (via LangChain4j) to get next direction
* Publish movement decision to shared decisions topic

### Key Classes

| Class | Role |
|---|---|
| `AgentDecider` | Kafka consumer; manages the latest-value slot and virtual thread executor |
| `LlmGateway` | CDI bean wrapping the LLM call with `@Timeout`, `@Retry`, `@CircuitBreaker`, `@Fallback` |
| `SnakeDecisionAi` | LangChain4j `@RegisterAiService` interface — the actual OpenAI call |

---

### Processing Model

* **Latest-value slot:** incoming perception messages overwrite an `AtomicReference` — only the freshest state is ever processed
* An `AtomicBoolean` gate ensures at most one LLM call is in-flight at a time; arriving messages during processing are dropped (overwrite the slot)
* LLM calls run on virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`
* The Kafka consumer handler (`@Incoming`) is annotated `@RunOnVirtualThread`

---

### Decision Strategy

* **Single-step:** send current state as prompt, receive direction
* Input to LLM: grid size, head position, full body cells, current direction, visible food, nearby snake segments
* Output from LLM: one of `UP`, `DOWN`, `LEFT`, `RIGHT`
* Model: configurable via `AGENT_LLM_MODEL` env var, default `gpt-4o-mini` (OpenAI)
* API key: `LLM_API_KEY` env var

---

## 9. Time Model

### Decoupled Time

* Engine runs continuously at fixed tick rate
* Agents respond asynchronously — no synchronization barrier
* Agent slow or absent → snake continues in last known direction

---

## 10. Web Interface

### Rendering

* HTML5 Canvas, 30×30 grid
* Each snake rendered in a distinct color keyed to `agentId`
* Food rendered as distinct markers

---

### Communication

* Single WebSocket endpoint on the engine (`/ws/game`)
* Engine pushes render-state JSON on every tick

---

## 11. Observability

### Infrastructure

All observability components run in Docker Compose alongside the application services:

* **Grafana** — dashboards and visualization
* **Loki** — log aggregation (Quarkus logs shipped via OTLP or Loki appender)
* **Tempo** — distributed trace storage (receives OTLP from Quarkus services)
* **Mimir / Prometheus** — metrics storage; Prometheus scrapes `/q/metrics` on each Quarkus service

---

### Metrics (Micrometer → Prometheus)

| Metric | Description |
|---|---|
| `snake_tick_duration_ms` | Time to process one engine tick |
| `snake_agent_response_time_ms` | Time from perception publish to decision received |
| `snake_active_agents` | Number of currently alive snakes |
| `snake_collisions_total` | Counter of collision events |
| `snake_food_consumed_total` | Counter of food consumption events |

---

### Tracing (OpenTelemetry → Tempo)

Trace the full decision flow:

```
Engine (publish perception)
  → Kafka
    → Agent (receive perception → LLM call → publish decision)
      → Kafka
        → Engine (receive decision)
```

Each engine tick and each agent LLM call are traced spans.

---

### Logs (Loki)

Structured JSON logs including:

* Agent decisions (agentId, direction, basedOnTick, decisionTimeMs)
* Collision events
* Timeouts / missing decisions
* Errors

---

## 12. Infrastructure (Docker Compose)

Services in `docker-compose.yml`:

| Service | Image |
|---|---|
| `kafka` | `confluentinc/cp-kafka` |
| `engine` | Built from source |
| `agent-{1..N}` | Built from source (same image, different env) |
| `prometheus` | `prom/prometheus` |
| `grafana` | `grafana/grafana` |
| `loki` | `grafana/loki` |
| `tempo` | `grafana/tempo` |

Agent count is controlled by Docker Compose profiles or scaling (`--scale agent=N`).

---

## 13. Failure Scenarios

### Agent Slow

* Engine continues at tick rate
* Snake moves in last known direction

---

### Agent Down

* Snake moves deterministically in last direction
* Eventually dies (wall or collision)
* Engine does not stall

---

### Messaging Delay

* Decisions arrive late and are applied on the next eligible tick
* System remains consistent — no state corruption

---

## 14. Future Enhancements

* Dynamic obstacles
* Team-based agents
* Reinforcement learning agents
* Adaptive difficulty
* Replay system
* Leaderboard / scoring persistence

---

## 15. Key Design Principles

* Event-driven architecture
* Loose coupling
* Eventual consistency
* Fault tolerance
* Observability-first design

---

## 16. Conclusion

This project demonstrates how **distributed multi-agent systems** behave under real-world constraints such as latency, partial information, and failure.

It is designed not only as a game simulation, but as a **reference architecture for resilient AI-driven systems**.
