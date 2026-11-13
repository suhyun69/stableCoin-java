package com.example.demo.outbox.adapter.out.kafka;

import static org.mockito.Mockito.*;

import com.example.demo.config.MessagingProperties;
import com.example.demo.outbox.domain.OutboxMessage;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaSenderKeyTest {
  @Test
  @SuppressWarnings("unchecked")
  void keysMessagesByPersistedSender() {
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var properties = mock(MessagingProperties.class);
    var jdbc = mock(JdbcTemplate.class);
    when(properties.topic()).thenReturn("payouts");
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(jdbc.queryForObject(anyString(), eq(String.class), eq("pay_a"))).thenReturn("0xaaa");
    when(jdbc.queryForObject(anyString(), eq(String.class), eq("pay_b"))).thenReturn("0xbbb");
    var publisher = new KafkaEventPublisherAdapter(kafka, properties, 31337, jdbc);
    publisher.publish(new OutboxMessage("out_a", "pay_a", "a", "owner", 1));
    publisher.publish(new OutboxMessage("out_b", "pay_b", "b", "owner", 1));
    verify(kafka).send("payouts", "31337:0xaaa", "a");
    verify(kafka).send("payouts", "31337:0xbbb", "b");
  }
}
