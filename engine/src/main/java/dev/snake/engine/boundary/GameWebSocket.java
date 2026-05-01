package dev.snake.engine.boundary;

import dev.snake.engine.Broadcaster;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

@WebSocket(path = "/ws/game")
public class GameWebSocket {

    final Broadcaster broadcaster;

    @Inject
    GameWebSocket(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnOpen
    void onOpen(WebSocketConnection connection) {
        broadcaster.register(connection);
    }

    @OnClose
    void onClose(WebSocketConnection connection) {
        broadcaster.unregister(connection);
    }
}
