package com.prp.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Entry point from Kafka. Bad records end up on the .DLT topic (see KafkaConfig). */
@Component
public class TransactionConsumer {

    private final PaymentIngestionService ingestion;
    private final ObjectMapper mapper;

    public TransactionConsumer(PaymentIngestionService ingestion, ObjectMapper mapper) {
        this.ingestion = ingestion;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${app.topics.transactions}", groupId = "recovery-engine", concurrency = "3")
    public void onTransaction(String json) throws Exception {
        ingestion.ingest(mapper.readValue(json, TransactionEvent.class));
    }
}
