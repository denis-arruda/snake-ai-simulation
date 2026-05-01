package dev.snake.common.entity;

import java.util.List;

public record AgentState(
        int tick,
        String agentId,
        SnakeInfo self,
        List<Position> visibleFood,
        List<SnakeSegment> nearbySnakes,
        int windowSize) {

    public record SnakeInfo(Position head, Direction direction, int length) {}

    public record Position(int x, int y) {}

    public record SnakeSegment(String agentId, int x, int y) {}
}
