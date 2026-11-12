package com.example.demo.reconciliation.adapter.out.kafka;

import com.example.demo.config.MessagingProperties;
import com.example.demo.reconciliation.application.port.out.ReconciliationJobsPort;
import com.example.demo.reconciliation.domain.ReconciliationRequestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "stablepay.messaging.enabled", havingValue = "true")
public class KafkaReconciliationJobsAdapter implements ReconciliationJobsPort {
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper mapper;
  private final MessagingProperties properties;

  public KafkaReconciliationJobsAdapter(
      KafkaTemplate<String, String> kafka, ObjectMapper mapper, MessagingProperties properties) {
    this.kafka = kafka;
    this.mapper = mapper;
    this.properties = properties;
  }

  public void enqueue(ReconciliationRequestedEvent event) {
    try {
      kafka
          .send(properties.reconciliationTopic(), "ledger", mapper.writeValueAsString(event))
          .get(20, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("reconciliation enqueue interrupted", e);
    } catch (Exception e) {
      throw new IllegalStateException("reconciliation enqueue failed", e);
    }
  }
}
