package dev.snake.agent.control;

import org.eclipse.microprofile.faulttolerance.ExecutionContext;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;

public class LlmFallbackHandler implements FallbackHandler<String> {

    static final System.Logger LOGGER = System.getLogger(LlmFallbackHandler.class.getName());

    @Override
    public String handle(ExecutionContext context) {
        LOGGER.log(System.Logger.Level.WARNING,
                "LLM unavailable — skipping decision, snake holds direction. Cause: {0}",
                context.getFailure().toString());
        return null;
    }
}
