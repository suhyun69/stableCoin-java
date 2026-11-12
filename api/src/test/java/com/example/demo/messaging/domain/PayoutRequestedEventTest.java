package com.example.demo.messaging.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PayoutRequestedEventTest {
  @Test
  void rejectsUnsupportedVersionAndRoundTripsContract() throws Exception {
    var mapper = new ObjectMapper();
    var event = new PayoutRequestedEvent("out_1", "PAYOUT_REQUESTED", 1, "pay_1");
    assertEquals(
        event, mapper.readValue(mapper.writeValueAsString(event), PayoutRequestedEvent.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PayoutRequestedEvent("out_1", "PAYOUT_REQUESTED", 2, "pay_1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PayoutRequestedEvent(null, "PAYOUT_REQUESTED", 1, "pay_1"));
  }
}
