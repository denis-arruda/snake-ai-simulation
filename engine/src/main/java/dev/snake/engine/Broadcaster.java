package dev.snake.engine;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class Broadcaster {

    static final System.Logger LOGGER = System.getLogger(Broadcaster.class.getName());

    final Set<WebSocketConnection> connections = ConcurrentHashMap.newKeySet();

    public void register(WebSocketConnection connection) {
        connections.add(connection);
    }

    public void unregister(WebSocketConnection connection) {
        connections.remove(connection);
    }

    public void broadcast(String message) {
        connections.forEach(conn -> sendSafely(conn, message));
    }

    void sendSafely(WebSocketConnection conn, String message) {
        try {
            conn.sendText(message).await().indefinitely();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to send to WebSocket client, removing connection", e);
            connections.remove(conn);
        }
    }
}
