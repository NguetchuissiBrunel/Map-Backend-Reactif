package com.example.Mp_Reactif.service;

import com.example.Mp_Reactif.event.PlaceSearchedEvent;
import com.example.Mp_Reactif.event.RouteCalculatedEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.logging.Logger;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class EventProducer {

    private static final Logger LOGGER = Logger.getLogger(EventProducer.class.getName());
    private static final String TOPIC_ROUTE = "route-events";
    private static final String TOPIC_PLACE = "place-events";

    private final KafkaSender<String, Object> kafkaSender;

    public EventProducer(KafkaSender<String, Object> kafkaSender) {
        this.kafkaSender = kafkaSender;
    }

    public Mono<Void> publishRouteCalculated(RouteCalculatedEvent event) {
        LOGGER.info("📤 Publishing RouteCalculatedEvent: " + event);
        SenderRecord<String, Object, Integer> record = SenderRecord.create(
                new ProducerRecord<>(TOPIC_ROUTE, event.getStartPlace() + "-" + event.getEndPlace(), event),
                1);

        return kafkaSender.send(Mono.just(record))
                .doOnError(e -> LOGGER.severe("❌ Error publishing route event: " + e.getMessage()))
                .next()
                .then();
    }

    public Mono<Void> publishPlaceSearched(PlaceSearchedEvent event) {
        LOGGER.info("📤 Publishing PlaceSearchedEvent: " + event);
        SenderRecord<String, Object, Integer> record = SenderRecord.create(
                new ProducerRecord<>(TOPIC_PLACE, event.getQuery(), event),
                1);

        return kafkaSender.send(Mono.just(record))
                .doOnError(e -> LOGGER.severe("❌ Error publishing place event: " + e.getMessage()))
                .next()
                .then();
    }
}
