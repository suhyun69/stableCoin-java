package com.example.demo.outbox.application.port.out;

import com.example.demo.outbox.domain.OutboxMessage;

public interface EventPublisherPort {
  void publish(OutboxMessage message);
}
