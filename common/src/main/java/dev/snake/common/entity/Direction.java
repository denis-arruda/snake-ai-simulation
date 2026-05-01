package dev.snake.common.entity;

public enum Direction {
    UP, DOWN, LEFT, RIGHT;

    public static Direction parse(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
