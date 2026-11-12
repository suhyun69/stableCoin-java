package com.example.demo.outbox.application.port.out;

import com.example.demo.outbox.domain.OutboxMessage;
import java.util.Optional;

public interface OutboxStorePort {
  Optional<OutboxMessage> claim(String owner);

  boolean published(OutboxMessage message);

  void failed(OutboxMessage message, String error);
}
