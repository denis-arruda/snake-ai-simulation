package dev.snake.engine.entity;

import dev.snake.common.entity.AgentDecision;
import dev.snake.common.entity.AgentState;
import dev.snake.common.entity.Direction;
import dev.snake.common.entity.RenderState;
import dev.snake.common.entity.RenderState.Position;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @ConfigProperty(name = "game.decision.max-age-ticks", defaultValue = "5")
    int maxDecisionAgeTicks;

    int tick;
    List<Snake> snakes;
    List<Position> foods;

    @PostConstruct
    public void restart() {
        tick   = 0;
        snakes = List.of(randomSnake("agent-1"));
        var occupied = snakes.stream().flatMap(s -> s.cells.stream()).toList();
        foods  = new ArrayList<>(randomPositions(maxFood, occupied));
    }

    public void applyDecisions(Map<String, AgentDecision> decisions) {
        snakes.stream()
                .filter(s -> s.alive)
                .forEach(s -> {
                    var decision = decisions.get(s.agentId);
                    if (decision == null) return;
                    if (tick - decision.basedOnTick() > maxDecisionAgeTicks) return;
                    if (isReversal(decision.direction(), s.direction)) return;
                    s.direction = decision.direction();
                });
    }

    boolean isReversal(Direction requested, Direction current) {
        return switch (requested) {
            case UP    -> current == Direction.DOWN;
            case DOWN  -> current == Direction.UP;
            case LEFT  -> current == Direction.RIGHT;
            case RIGHT -> current == Direction.LEFT;
        };
    }

    public void advance() {
        if (snakes.stream().noneMatch(s -> s.alive)) return;
        tick++;
        snakes.stream().filter(s -> s.alive).forEach(this::advanceSnake);
    }

    void advanceSnake(Snake snake) {
        var newHead = nextHead(snake);
        if (isOutOfBounds(newHead) || snake.cells.contains(newHead)) {
            snake.kill();
        } else if (foods.remove(newHead)) {
            snake.grow(newHead);
            spawnFood();
        } else {
            snake.move(newHead);
        }
    }

    void spawnFood() {
        var occupied = Stream.concat(snakes.stream().flatMap(s -> s.cells.stream()), foods.stream()).toList();
        foods.addAll(randomPositions(1, occupied));
    }

    Position nextHead(Snake snake) {
        var head = snake.cells.get(0);
        return switch (snake.direction) {
            case RIGHT -> new Position(head.x() + 1, head.y());
            case LEFT  -> new Position(head.x() - 1, head.y());
            case DOWN  -> new Position(head.x(),      head.y() + 1);
            case UP    -> new Position(head.x(),      head.y() - 1);
        };
    }

    boolean isOutOfBounds(Position pos) {
        return pos.x() < 0 || pos.x() >= gridSize || pos.y() < 0 || pos.y() >= gridSize;
    }

    public RenderState toRenderState() {
        var snakeRenders = snakes.stream().map(Snake::toRender).toList();
        return new RenderState(tick, snakeRenders, List.copyOf(foods));
    }

    public List<AgentState> toAgentStates(int windowSize) {
        int half = windowSize / 2;
        return snakes.stream()
                .filter(s -> s.alive)
                .map(s -> toAgentState(s, half, windowSize))
                .toList();
    }

    AgentState toAgentState(Snake snake, int half, int windowSize) {
        var head = snake.cells.get(0);

        var body = snake.cells.stream()
                .map(p -> new AgentState.Position(p.x(), p.y()))
                .toList();

        var self = new AgentState.SnakeInfo(
                new AgentState.Position(head.x(), head.y()),
                snake.direction,
                snake.cells.size(),
                body);

        var visibleFood = foods.stream()
                .filter(f -> inWindow(f, head, half))
                .map(f -> new AgentState.Position(f.x(), f.y()))
                .toList();

        var nearbySnakes = snakes.stream()
                .filter(other -> !other.agentId.equals(snake.agentId) && other.alive)
                .flatMap(other -> other.cells.stream()
                        .filter(p -> inWindow(p, head, half))
                        .map(p -> new AgentState.SnakeSegment(other.agentId, p.x(), p.y())))
                .toList();

        return new AgentState(tick, snake.agentId, self, visibleFood, nearbySnakes, windowSize, gridSize);
    }

    boolean inWindow(Position p, Position head, int half) {
        return Math.abs(p.x() - head.x()) <= half && Math.abs(p.y() - head.y()) <= half;
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
