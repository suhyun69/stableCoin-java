package com.example.demo.messaging.domain;

public record PayoutRequestedEvent(
    String eventId, String eventType, int schemaVersion, String payoutId) {
  public PayoutRequestedEvent {
    if (eventId == null
        || !eventId.startsWith("out_")
        || eventId.length() > 100
        || !"PAYOUT_REQUESTED".equals(eventType)
        || schemaVersion != 1
        || payoutId == null
        || !payoutId.startsWith("pay_")
        || payoutId.length() > 100) {
      throw new IllegalArgumentException("unsupported payout event");
    }
  }
}
