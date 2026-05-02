package dev.snake.engine.boundary;

import dev.snake.engine.entity.GameState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/game")
@ApplicationScoped
public class GameResource {

    final GameState gameState;

    @Inject
    GameResource(GameState gameState) {
        this.gameState = gameState;
    }

    @POST
    @Path("/restart")
    public Response restart() {
        gameState.restart();
        return Response.noContent().build();
    }
}
