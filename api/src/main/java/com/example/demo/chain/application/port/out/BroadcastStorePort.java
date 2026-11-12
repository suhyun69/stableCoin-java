package com.example.demo.chain.application.port.out;

public interface BroadcastStorePort {
  boolean processed(String eventId, String payoutId);

  void validateEvent(String eventId, String payoutId);

  void complete(String eventId, String payoutId, boolean accepted, String error);
}
