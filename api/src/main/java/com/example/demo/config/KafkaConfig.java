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
    // Until the explicit DLT policy is installed, never discard a failed payout.
    @Bean org.springframework.kafka.listener.DefaultErrorHandler payoutErrorHandler() {
        return new org.springframework.kafka.listener.DefaultErrorHandler(new org.springframework.util.backoff.FixedBackOff(1000L,Long.MAX_VALUE));
    }
    @Bean NewTopic payoutRequestedTopic(MessagingProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(properties.partitions()).replicas(1).build();
    }
    @Bean NewTopic payoutDeadLetterTopic(MessagingProperties properties) {
        return TopicBuilder.name(properties.deadLetterTopic()).partitions(properties.partitions()).replicas(1).build();
    }
}
