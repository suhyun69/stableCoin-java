package com.example.demo.outbox.adapter.out.kafka;

import com.example.demo.config.MessagingProperties;
import com.example.demo.outbox.application.port.out.EventPublisherPort;
import com.example.demo.outbox.domain.OutboxMessage;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "stablepay.messaging.enabled", havingValue = "true")
public class KafkaEventPublisherAdapter implements EventPublisherPort {
  private final KafkaTemplate<String, String> kafka;
  private final MessagingProperties properties;
  private final long chainId;
  private final JdbcTemplate jdbc;

  public KafkaEventPublisherAdapter(
      KafkaTemplate<String, String> kafka,
      MessagingProperties properties,
      @Value("${stablepay.chain.id:31337}") long chainId,
      JdbcTemplate jdbc) {
    this.kafka = kafka;
    this.properties = properties;
    this.chainId = chainId;
    this.jdbc = jdbc;
  }

  public void publish(OutboxMessage message) {
    try {
      String sender =
          jdbc.queryForObject(
              "SELECT sender_address FROM payouts WHERE id=?", String.class, message.payoutId());
      String key = chainId + ":" + sender;
      kafka.send(properties.topic(), key, message.payload()).get(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Kafka publish interrupted", e);
    } catch (Exception e) {
      throw new IllegalStateException("Kafka publish failed", e);
    }
  }
}
