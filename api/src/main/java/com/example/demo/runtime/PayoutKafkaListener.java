package com.example.demo.runtime;

import com.example.demo.messaging.domain.PayoutRequestedEvent;
import com.example.demo.usecase.SendPayoutUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"stablepay.messaging.enabled", "stablepay.chain.enabled"},
    havingValue = "true")
public class PayoutKafkaListener {
  private final ObjectMapper mapper;
  private final SendPayoutUseCase sender;

  public PayoutKafkaListener(ObjectMapper mapper, SendPayoutUseCase sender) {
    this.mapper = mapper;
    this.sender = sender;
  }

  @KafkaListener(
      topics = "${stablepay.messaging.topic}",
      groupId = "${stablepay.messaging.group}",
      autoStartup = "${stablepay.workers.enabled:false}")
  public void receive(String payload) {
    PayoutRequestedEvent event;
    try {
      event = mapper.readValue(payload, PayoutRequestedEvent.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid payout event", e);
    }
    sender.send(event.eventId(), event.payoutId());
  }
}
