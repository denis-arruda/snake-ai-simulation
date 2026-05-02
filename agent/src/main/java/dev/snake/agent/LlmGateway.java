package dev.snake.agent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class LlmGateway {

    final SnakeDecisionAi ai;

    @Inject
    LlmGateway(SnakeDecisionAi ai) {
        this.ai = ai;
    }

    @Timeout(value = 12, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 500, delayUnit = ChronoUnit.MILLIS, abortOn = TimeoutException.class)
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 0.6, delay = 30, delayUnit = ChronoUnit.SECONDS)
    @Fallback(LlmFallbackHandler.class)
    public String decide(String prompt) {
        return ai.decide(prompt);
    }
}
