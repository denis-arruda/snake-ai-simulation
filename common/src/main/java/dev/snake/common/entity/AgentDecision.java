package dev.snake.common.entity;

public record AgentDecision(
        String agentId,
        Direction direction,
        int basedOnTick,
        long decisionTimeMs) {
}
