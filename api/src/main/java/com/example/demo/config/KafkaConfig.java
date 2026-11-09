package com.example.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name="stablepay.messaging.enabled", havingValue="true")
@EnableConfigurationProperties(MessagingProperties.class)
public class KafkaConfig {
    @Bean public org.springframework.kafka.listener.DeadLetterPublishingRecoverer payoutRecoverer(
            org.springframework.kafka.core.KafkaTemplate<String,String> kafka, MessagingProperties properties) {
        var recoverer=new org.springframework.kafka.listener.DeadLetterPublishingRecoverer(kafka,
            (record,error) -> new org.apache.kafka.common.TopicPartition(record.topic()+".DLT",record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(java.time.Duration.ofSeconds(20));
        return recoverer;
    }
    @Bean public org.springframework.kafka.listener.DefaultErrorHandler payoutErrorHandler(
            org.springframework.kafka.listener.DeadLetterPublishingRecoverer recoverer) {
        var backoff=new org.springframework.kafka.support.ExponentialBackOffWithMaxRetries(4);
        backoff.setInitialInterval(500);backoff.setMultiplier(2);backoff.setMaxInterval(4000);
        var handler=new org.springframework.kafka.listener.DefaultErrorHandler(recoverer,backoff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class,
            com.example.demo.common.exception.InvalidInputException.class,
            com.example.demo.common.exception.ConflictException.class,
            com.example.demo.common.exception.NotFoundException.class);
        return handler;
    }
    @Bean NewTopic reconciliationTopic(MessagingProperties properties) {
        return TopicBuilder.name(properties.reconciliationTopic()).partitions(properties.partitions()).replicas(1).build();
    }
    @Bean NewTopic reconciliationDeadLetterTopic(MessagingProperties properties) {
        return TopicBuilder.name(properties.reconciliationTopic()+".DLT").partitions(properties.partitions()).replicas(1).build();
    }
    @Bean NewTopic payoutRequestedTopic(MessagingProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(properties.partitions()).replicas(1).build();
    }
    @Bean NewTopic payoutDeadLetterTopic(MessagingProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic()).partitions(properties.partitions()).replicas(1).build();
    }
}
