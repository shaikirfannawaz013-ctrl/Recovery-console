package com.prp.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /** Raw transactions from payment gateways (failed and successful). */
    @Bean
    public NewTopic transactionsTopic(AppProperties props) {
        return TopicBuilder.name(props.topics().transactions()).partitions(3).replicas(1).build();
    }

    /** Decisions made by the platform, relayed to the dashboard over WebSocket. */
    @Bean
    public NewTopic recoveryEventsTopic(AppProperties props) {
        return TopicBuilder.name(props.topics().recoveryEvents()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic deadLetterTopic(AppProperties props) {
        return TopicBuilder.name(props.topics().transactions() + ".DLT").partitions(1).replicas(1).build();
    }

    /** Retry a bad record twice, then park it on the .DLT topic instead of blocking the partition. */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 2));
    }
}
