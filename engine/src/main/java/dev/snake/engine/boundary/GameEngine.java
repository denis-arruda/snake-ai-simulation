package dev.snake.engine.boundary;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.snake.common.entity.AgentDecision;
import dev.snake.common.entity.AgentState;
import dev.snake.common.entity.RenderState;
import dev.snake.engine.Broadcaster;
import dev.snake.engine.entity.GameState;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class GameEngine {

    static final System.Logger LOGGER = System.getLogger(GameEngine.class.getName());

    final ObjectMapper objectMapper;
    final GameState gameState;
    final Broadcaster broadcaster;
    final PerceptionPublisher perceptionPublisher;

    @ConfigProperty(name = "game.agent.window-size", defaultValue = "7")
    int agentWindowSize;

    @Channel("render-state")
    Emitter<String> renderStateEmitter;

    final ConcurrentHashMap<String, AgentDecision> latestDecisions = new ConcurrentHashMap<>();

    @Inject
    GameEngine(ObjectMapper objectMapper, GameState gameState, Broadcaster broadcaster,
               PerceptionPublisher perceptionPublisher) {
        this.objectMapper = objectMapper;
        this.gameState = gameState;
        this.broadcaster = broadcaster;
        this.perceptionPublisher = perceptionPublisher;
    }

    public void restart() {
        latestDecisions.clear();
        gameState.restart();
    }

    @Incoming("agent-decisions")
    @RunOnVirtualThread
    public void onDecision(String decisionJson) {
        try {
            var decision = objectMapper.readValue(decisionJson, AgentDecision.class);
            latestDecisions.put(decision.agentId(), decision);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to deserialize agent decision", e);
        }
    }

    @Scheduled(every = "${game.tick.interval:200ms}")
    @RunOnVirtualThread
    @Timeout(value = 1, unit = ChronoUnit.SECONDS)
    public void tick() {
        try {
            gameState.applyDecisions(latestDecisions);
            gameState.advance();
            RenderState renderState = gameState.toRenderState();
            var json = objectMapper.writeValueAsString(renderState);

            renderStateEmitter.send(json);
            broadcaster.broadcast(json);

            for (var agentState : gameState.toAgentStates(agentWindowSize)) {
                publishPerception(agentState);
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Tick processing failed", e);
        }
    }

    void publishPerception(AgentState agentState) {
        try {
            perceptionPublisher.publish(agentState);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to publish perception for agent {0}", agentState.agentId(), e);
        }
    }
}
