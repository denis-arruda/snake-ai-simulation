package dev.snake.agent.boundary;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.snake.agent.control.LlmGateway;
import dev.snake.common.entity.AgentDecision;
import dev.snake.common.entity.AgentState;
import dev.snake.common.entity.Direction;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class AgentDecider {

    static final System.Logger LOGGER = System.getLogger(AgentDecider.class.getName());

    final String agentId;
    final ObjectMapper objectMapper;
    final LlmGateway llmGateway;
    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    final AtomicReference<AgentState> latest = new AtomicReference<>();
    final AtomicBoolean processing = new AtomicBoolean(false);

    @Channel("agent-decisions")
    Emitter<String> decisionEmitter;

    @Inject
    AgentDecider(
            @ConfigProperty(name = "agent.id") String agentId,
            ObjectMapper objectMapper,
            LlmGateway llmGateway) {
        this.agentId = agentId;
        this.objectMapper = objectMapper;
        this.llmGateway = llmGateway;
    }

    @Incoming("agent-perception")
    @RunOnVirtualThread
    public void onPerception(String stateJson) {
        try {
            var state = objectMapper.readValue(stateJson, AgentState.class);
            latest.set(state);
            tryProcess();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to deserialize perception for agent {0}", agentId, e);
        }
    }

    void tryProcess() {
        if (processing.compareAndSet(false, true)) {
            executor.submit(this::processLatest);
        }
    }

    void processLatest() {
        try {
            var state = latest.getAndSet(null);
            if (state == null) {
                return;
            }
            var start = System.currentTimeMillis();
            var raw = llmGateway.decide(buildPrompt(state));
            if (raw == null) {
                return;
            }
            var direction = Direction.parse(raw);
            var decision = new AgentDecision(agentId, direction, state.tick(), System.currentTimeMillis() - start);
            decisionEmitter.send(objectMapper.writeValueAsString(decision));
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to process perception for agent {0}", agentId, e);
        } finally {
            processing.set(false);
            if (latest.get() != null) {
                tryProcess();
            }
        }
    }

    String buildPrompt(AgentState state) {
        var head = state.self().head();
        var body = state.self().body();
        int g    = state.gridSize();
        var enemyPositions = state.nearbySnakes().stream()
                .map(s -> new AgentState.Position(s.x(), s.y()))
                .toList();

        var moves = new StringBuilder();
        for (var dir : Direction.values()) {
            var next       = step(head, dir);
            var danger     = isOutOfBounds(next, g)      ? "WALL — instant death"
                           : body.contains(next)          ? "BODY — instant death"
                           : enemyPositions.contains(next) ? "SNAKE — instant death"
                           : "safe";
            var wallDist   = wallDistance(head, dir, g);
            var snakeDist  = snakeDistance(next, enemyPositions);
            var snakeInfo  = snakeDist == Integer.MAX_VALUE
                           ? "no snake nearby"
                           : snakeDist + " step" + (snakeDist == 1 ? "" : "s") + " to snake";
            moves.append("  %-5s → (%2d,%2d)  %-22s  (%d step%s to wall)  (%s)%n"
                    .formatted(dir, next.x(), next.y(), danger, wallDist, wallDist == 1 ? "" : "s", snakeInfo));
        }

        var food = state.visibleFood().isEmpty() ? "none visible" : state.visibleFood().toString();

        return """
                Grid %dx%d  |  x=0..%d left→right, y=0..%d top→bottom  |  currently moving %s
                Head at (%d,%d).

                Move options:
                %s
                Visible food: %s

                Avoid WALL, BODY, and SNAKE moves — all cause instant death.
                Prefer moves with more steps to wall and more steps to snake.
                Reply with exactly one word: UP DOWN LEFT RIGHT
                """.formatted(
                g, g, g - 1, g - 1, state.self().direction(),
                head.x(), head.y(),
                moves,
                food);
    }

    AgentState.Position step(AgentState.Position pos, Direction dir) {
        return switch (dir) {
            case UP    -> new AgentState.Position(pos.x(), pos.y() - 1);
            case DOWN  -> new AgentState.Position(pos.x(), pos.y() + 1);
            case LEFT  -> new AgentState.Position(pos.x() - 1, pos.y());
            case RIGHT -> new AgentState.Position(pos.x() + 1, pos.y());
        };
    }

    boolean isOutOfBounds(AgentState.Position pos, int g) {
        return pos.x() < 0 || pos.x() >= g || pos.y() < 0 || pos.y() >= g;
    }

    int snakeDistance(AgentState.Position from, java.util.List<AgentState.Position> enemies) {
        return enemies.stream()
                .mapToInt(e -> Math.abs(e.x() - from.x()) + Math.abs(e.y() - from.y()))
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    int wallDistance(AgentState.Position head, Direction dir, int g) {
        return switch (dir) {
            case UP    -> head.y();
            case DOWN  -> g - 1 - head.y();
            case LEFT  -> head.x();
            case RIGHT -> g - 1 - head.x();
        };
    }

    @PreDestroy
    void close() {
        executor.shutdown();
    }
}
