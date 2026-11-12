package com.example.demo.runtime;

import com.example.demo.reconciliation.domain.ReconciliationRequestedEvent;
import com.example.demo.usecase.RunReconciliationUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"stablepay.messaging.enabled", "stablepay.chain.enabled"},
    havingValue = "true")
public class ReconciliationKafkaListener {
  private final ObjectMapper mapper;
  private final RunReconciliationUseCase run;

  public ReconciliationKafkaListener(ObjectMapper mapper, RunReconciliationUseCase run) {
    this.mapper = mapper;
    this.run = run;
  }

  @KafkaListener(
      topics = "${stablepay.messaging.topic}.reconciliation",
      groupId = "${stablepay.messaging.group}.reconciliation",
      autoStartup = "${stablepay.workers.enabled:false}")
  public void receive(String payload) {
    ReconciliationRequestedEvent event;
    try {
      event = mapper.readValue(payload, ReconciliationRequestedEvent.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid reconciliation event", e);
    }
    run.run(event.fromBlock(), event.toBlock());
  }
}
