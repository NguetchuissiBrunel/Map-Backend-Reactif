package com.example.Mp_Reactif.service;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.kafka.receiver.KafkaReceiver;

import java.util.logging.Logger;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class EventConsumer {

    private static final Logger LOGGER = Logger.getLogger(EventConsumer.class.getName());

    private final KafkaReceiver<String, Object> kafkaReceiver;

    public EventConsumer(KafkaReceiver<String, Object> kafkaReceiver) {
        this.kafkaReceiver = kafkaReceiver;
    }

    @PostConstruct
    public void startConsuming() {
        kafkaReceiver.receive()
                .doOnNext(record -> {
                    Object value = record.value();
                    LOGGER.info("📥 Event Received: Topic=" + record.topic() + ", Value=" + value);
                    // In a real microservices architecture, you would process the event here.
                    // For now, we just acknowledge.
                    record.receiverOffset().acknowledge();
                })
                .doOnError(e -> LOGGER.severe("❌ Error consuming event: " + e.getMessage()))
                .subscribe();
    }
}
