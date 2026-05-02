package dev.snake.engine.entity;

import dev.snake.common.entity.Direction;
import dev.snake.common.entity.RenderState;
import dev.snake.common.entity.RenderState.Position;

import java.util.ArrayList;
import java.util.List;

public class Snake {

    String agentId;
    List<Position> cells;
    Direction direction;
    boolean alive;

    Snake(String agentId, List<Position> cells, Direction direction) {
        this.agentId = agentId;
        this.cells = cells;
        this.direction = direction;
        this.alive = true;
    }

    void move(Position newHead) {
        var updated = new ArrayList<>(cells);
        updated.add(0, newHead);
        updated.remove(updated.size() - 1);
        cells = updated;
    }

    void kill() {
        alive = false;
    }

    RenderState.SnakeRender toRender() {
        return new RenderState.SnakeRender(agentId, List.copyOf(cells), direction, alive);
    }
}
