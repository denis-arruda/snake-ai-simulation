package dev.snake.agent.boundary;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.snake.agent.SnakeDecisionAi;
import dev.snake.common.entity.AgentDecision;
import dev.snake.common.entity.AgentState;
import dev.snake.common.entity.Direction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class AgentDecider {

    static final System.Logger LOGGER = System.getLogger(AgentDecider.class.getName());

    final String agentId;
    final ObjectMapper objectMapper;
    final SnakeDecisionAi decisionAi;

    @Channel("agent-decisions")
    Emitter<String> decisionEmitter;

    @Inject
    AgentDecider(
            @ConfigProperty(name = "agent.id") String agentId,
            ObjectMapper objectMapper,
            SnakeDecisionAi decisionAi) {
        this.agentId = agentId;
        this.objectMapper = objectMapper;
        this.decisionAi = decisionAi;
    }

    @Incoming("agent-perception")
    public void onPerception(String stateJson) {
        try {
            var state = objectMapper.readValue(stateJson, AgentState.class);
            var start = System.currentTimeMillis();

            var direction = Direction.parse(decisionAi.decide(buildPrompt(state)));

            var decision = new AgentDecision(agentId, direction, state.tick(), System.currentTimeMillis() - start);
            decisionEmitter.send(objectMapper.writeValueAsString(decision));
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to process perception for agent {0}", agentId, e);
        }
    }

    String buildPrompt(AgentState state) {
        return """
                Head: (%d,%d), direction: %s, visible food: %s, nearby snakes: %s.
                Reply with UP, DOWN, LEFT, or RIGHT only.
                """.formatted(
                state.self().head().x(), state.self().head().y(),
                state.self().direction(),
                state.visibleFood(),
                state.nearbySnakes());
    }
}
