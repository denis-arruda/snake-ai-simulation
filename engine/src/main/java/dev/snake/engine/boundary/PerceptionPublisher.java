package dev.snake.engine.boundary;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.snake.common.entity.AgentState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.time.temporal.ChronoUnit;
import java.util.Properties;

@ApplicationScoped
public class PerceptionPublisher {

    static final System.Logger LOGGER = System.getLogger(PerceptionPublisher.class.getName());

    final ObjectMapper objectMapper;

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    @ConfigProperty(name = "game.agent.perception.topic-base", defaultValue = "snake-agent-state")
    String agentTopicBase;

    KafkaProducer<String, String> kafkaProducer;

    @Inject
    PerceptionPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        var props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProducer = new KafkaProducer<>(props);
    }

    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 15, delayUnit = ChronoUnit.SECONDS)
    public void publish(AgentState agentState) throws Exception {
        var json = objectMapper.writeValueAsString(agentState);
        var topic = agentTopicBase + "-" + agentState.agentId();
        kafkaProducer.send(new ProducerRecord<>(topic, agentState.agentId(), json)).get();
    }

    @PreDestroy
    void close() {
        if (kafkaProducer != null) kafkaProducer.close();
    }
}
