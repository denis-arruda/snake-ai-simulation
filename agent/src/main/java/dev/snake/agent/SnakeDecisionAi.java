package dev.snake.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface SnakeDecisionAi {

    @SystemMessage("""
            You control a snake in a grid-based game.
            Respond with exactly one word — UP, DOWN, LEFT, or RIGHT.
            Avoid walls and other snakes. Move toward food when possible.
            """)
    String decide(@UserMessage String stateDescription);
}
