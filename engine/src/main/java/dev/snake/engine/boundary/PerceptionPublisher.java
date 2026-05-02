package dev.snake.engine.boundary;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.snake.common.entity.AgentState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PerceptionPublisher {

    static final System.Logger LOGGER = System.getLogger(PerceptionPublisher.class.getName());

    final ObjectMapper objectMapper;

    @ConfigProperty(name = "mp.messaging.connector.smallrye-pulsar.serviceUrl",
                    defaultValue = "pulsar://localhost:6650")
    String serviceUrl;

    @ConfigProperty(name = "game.agent.perception.topic-base",
                    defaultValue = "persistent://public/default/snake-agent-state")
    String agentTopicBase;

    PulsarClient pulsarClient;
    final ConcurrentHashMap<String, Producer<byte[]>> producers = new ConcurrentHashMap<>();

    @Inject
    PerceptionPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        try {
            pulsarClient = PulsarClient.builder()
                    .serviceUrl(serviceUrl)
                    .build();
        } catch (PulsarClientException e) {
            throw new RuntimeException("Failed to create Pulsar client for perception publishing", e);
        }
    }

    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 15, delayUnit = ChronoUnit.SECONDS)
    public void publish(AgentState agentState) throws Exception {
        var json = objectMapper.writeValueAsString(agentState);
        producerFor(agentState.agentId()).send(json.getBytes(StandardCharsets.UTF_8));
    }

    Producer<byte[]> producerFor(String agentId) throws PulsarClientException {
        var producer = producers.get(agentId);
        if (producer == null) {
            producer = pulsarClient.newProducer()
                    .topic(agentTopicBase + "-" + agentId)
                    .create();
            producers.put(agentId, producer);
        }
        return producer;
    }

    @PreDestroy
    void close() {
        producers.values().forEach(p -> {
            try { p.close(); } catch (PulsarClientException ignored) {}
        });
        try {
            if (pulsarClient != null) pulsarClient.close();
        } catch (PulsarClientException ignored) {}
    }
}
