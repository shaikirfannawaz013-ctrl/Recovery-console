package com.prp.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prp.common.Ids;
import com.prp.config.AppProperties;
import com.prp.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Publishes recovery events to Kafka. Inside a DB transaction the send is
 * deferred until commit, so the dashboard never sees a decision that rolled back.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public EventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper, AppProperties props) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = props.topics().recoveryEvents();
    }

    public void publish(EventType type, Payment p) {
        publish(new RecoveryEvent(Ids.next("EVT"), type, p.getId(), p.getCustomer().getName(), p.getAmount(),
                p.getCurrency(), p.getFailureReason() == null ? null : p.getFailureReason().name(), p.getGateway(), Instant.now()));
    }

    public void publishDuplicate(String duplicateId, String customerName, BigDecimal amount, String currency, String gateway) {
        publish(new RecoveryEvent(Ids.next("EVT"), EventType.DUPLICATE_BLOCKED, duplicateId, customerName, amount,
                currency, null, gateway, Instant.now()));
    }

    private void publish(RecoveryEvent event) {
        String json;
        try {
            json = mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        Runnable send = () -> kafka.send(topic, event.paymentId(), json)
                .whenComplete((r, ex) -> { if (ex != null) log.warn("Could not publish {}: {}", event.type(), ex.getMessage()); });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }
}
