package com.prp.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Forwards recovery events from Kafka to WebSocket clients. Each backend
 * instance uses its own consumer group so every instance's clients get every event.
 */
@Component
public class EventRelay {

    private final SimpMessagingTemplate ws;
    private final ObjectMapper mapper;

    public EventRelay(SimpMessagingTemplate ws, ObjectMapper mapper) {
        this.ws = ws;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = "${app.topics.recovery-events}",
            groupId = "ws-relay-#{T(java.util.UUID).randomUUID().toString()}",
            properties = "auto.offset.reset=latest")
    public void relay(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        ws.convertAndSend("/topic/transactions", node);
    }
}
