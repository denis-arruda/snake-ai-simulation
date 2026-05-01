package dev.snake.engine.boundary;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.snake.common.entity.AgentDecision;
import dev.snake.common.entity.RenderState;
import dev.snake.engine.Broadcaster;
import dev.snake.engine.entity.GameState;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class GameEngine {

    static final System.Logger LOGGER = System.getLogger(GameEngine.class.getName());

    final ObjectMapper objectMapper;
    final GameState gameState;
    final Broadcaster broadcaster;

    @Channel("render-state")
    Emitter<String> renderStateEmitter;

    final ConcurrentHashMap<String, AgentDecision> latestDecisions = new ConcurrentHashMap<>();

    @Inject
    GameEngine(ObjectMapper objectMapper, GameState gameState, Broadcaster broadcaster) {
        this.objectMapper = objectMapper;
        this.gameState = gameState;
        this.broadcaster = broadcaster;
    }

    @Incoming("agent-decisions")
    public void onDecision(String decisionJson) {
        try {
            var decision = objectMapper.readValue(decisionJson, AgentDecision.class);
            latestDecisions.put(decision.agentId(), decision);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to deserialize agent decision", e);
        }
    }

    @Scheduled(every = "${game.tick.interval:200ms}")
    void tick() {
        try {
            gameState.advance();
            RenderState renderState = gameState.toRenderState();
            var json = objectMapper.writeValueAsString(renderState);

            renderStateEmitter.send(json);
            broadcaster.broadcast(json);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Tick processing failed", e);
        }
    }
}
