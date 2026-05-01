package dev.snake.engine.entity;

import dev.snake.common.entity.RenderState;
import dev.snake.common.entity.RenderState.Position;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

@ApplicationScoped
public class GameState {

    @ConfigProperty(name = "game.grid.size", defaultValue = "30")
    int gridSize;

    @ConfigProperty(name = "game.food.max", defaultValue = "5")
    int maxFood;

    @ConfigProperty(name = "game.snake.initial-length", defaultValue = "3")
    int initialSnakeLength;

    int tick;
    List<Position> foods;

    @PostConstruct
    void initialize() {
        tick = 0;
        foods = new ArrayList<>(randomPositions(maxFood));
    }

    public void advance() {
        tick++;
        // TODO: move snakes, detect collisions, handle food consumption
    }

    public RenderState toRenderState() {
        return new RenderState(tick, List.of(), List.copyOf(foods));
    }

    public List<String> agentIds() {
        // TODO: return IDs of all registered agents
        return List.of();
    }

    List<Position> randomPositions(int count) {
        var rng = ThreadLocalRandom.current();
        return Stream.generate(() -> new Position(rng.nextInt(gridSize), rng.nextInt(gridSize)))
                .distinct()
                .limit(count)
                .toList();
    }
}
