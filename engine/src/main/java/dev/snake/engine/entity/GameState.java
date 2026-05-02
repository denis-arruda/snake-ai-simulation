package dev.snake.engine.entity;

import dev.snake.common.entity.Direction;
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
    List<Snake> snakes;
    List<Position> foods;

    @PostConstruct
    void initialize() {
        tick   = 0;
        snakes = List.of(randomSnake("agent-1"));
        var occupied = snakes.stream().flatMap(s -> s.cells.stream()).toList();
        foods  = new ArrayList<>(randomPositions(maxFood, occupied));
    }

    public void advance() {
        tick++;
        // TODO: move snakes, detect collisions, handle food consumption
    }

    public RenderState toRenderState() {
        var snakeRenders = snakes.stream().map(Snake::toRender).toList();
        return new RenderState(tick, snakeRenders, List.copyOf(foods));
    }

    public List<String> agentIds() {
        return snakes.stream().map(s -> s.agentId).toList();
    }

    Snake randomSnake(String agentId) {
        var direction = randomDirection();
        var head      = randomHead(direction);
        var cells     = buildBody(head, direction);
        return new Snake(agentId, cells, direction);
    }

    Direction randomDirection() {
        var values = Direction.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    Position randomHead(Direction direction) {
        var rng    = ThreadLocalRandom.current();
        var margin = initialSnakeLength - 1;
        return switch (direction) {
            case RIGHT -> new Position(rng.nextInt(margin, gridSize),          rng.nextInt(gridSize));
            case LEFT  -> new Position(rng.nextInt(0, gridSize - margin),      rng.nextInt(gridSize));
            case DOWN  -> new Position(rng.nextInt(gridSize),                  rng.nextInt(margin, gridSize));
            case UP    -> new Position(rng.nextInt(gridSize),                  rng.nextInt(0, gridSize - margin));
        };
    }

    List<Position> buildBody(Position head, Direction direction) {
        return Stream.iterate(head, prev -> behindOf(prev, direction))
                .limit(initialSnakeLength)
                .toList();
    }

    Position behindOf(Position pos, Direction direction) {
        return switch (direction) {
            case RIGHT -> new Position(pos.x() - 1, pos.y());
            case LEFT  -> new Position(pos.x() + 1, pos.y());
            case DOWN  -> new Position(pos.x(),      pos.y() - 1);
            case UP    -> new Position(pos.x(),      pos.y() + 1);
        };
    }

    List<Position> randomPositions(int count, List<Position> excluded) {
        var rng = ThreadLocalRandom.current();
        return Stream.generate(() -> new Position(rng.nextInt(gridSize), rng.nextInt(gridSize)))
                .filter(p -> !excluded.contains(p))
                .distinct()
                .limit(count)
                .toList();
    }
}
