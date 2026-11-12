package com.example.demo.reconciliation.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;

class ReconciliationPolicyTest {
  final ObservedEthTransfer observed =
      new ObservedEthTransfer(
          "hash", "sender", "recipient", BigInteger.ONE, 0, 10, "block", true, 2);

  ReconciliationEntry entry(String status, String chain, Long block) {
    return new ReconciliationEntry(
        "payout", "hash", "sender", "recipient", BigInteger.ONE, 0, block, status, chain, true);
  }

  @Test
  void classifiesLagMissingUnknownAndContradictoryFinalState() {
    assertEquals(
        "STATUS_LAG",
        ReconciliationPolicy.classify(
                List.of(entry("AWAITING_CONFIRMATION", "PENDING", null)), List.of(observed), 2)
            .getFirst()
            .kind());
    assertTrue(
        ReconciliationPolicy.classify(
                List.of(entry("SETTLED", "CONFIRMED", 10L)), List.of(observed), 2)
            .isEmpty());
    assertEquals(
        "MISSING_CHAIN_TRANSACTION",
        ReconciliationPolicy.classify(List.of(entry("SETTLED", "CONFIRMED", 10L)), List.of(), 2)
            .getFirst()
            .kind());
    assertEquals(
        "STATUS_MISMATCH",
        ReconciliationPolicy.classify(
                List.of(entry("FAILED", "MINED_REVERTED", 10L)), List.of(observed), 2)
            .getFirst()
            .kind());
    assertEquals(
        "CHAIN_TRANSACTION_WITHOUT_LEDGER",
        ReconciliationPolicy.classify(List.of(), List.of(observed), 2).getFirst().kind());
    assertEquals(
        2, ReconciliationPolicy.classify(List.of(), List.of(observed, observed), 2).size());
    var wrong =
        new ReconciliationEntry(
            "payout",
            "hash",
            "sender",
            "recipient",
            BigInteger.TWO,
            0,
            10L,
            "SETTLED",
            "CONFIRMED",
            true);
    assertEquals(
        "TRANSACTION_MISMATCH",
        ReconciliationPolicy.classify(List.of(wrong), List.of(observed), 2).getFirst().kind());
  }

  @Test
  void validatesBoundedBlockRange() {
    assertEquals(new BlockRange(0, 999), BlockRange.parse("0", "999"));
    assertThrows(IllegalArgumentException.class, () -> BlockRange.parse("0", "1000"));
    assertThrows(IllegalArgumentException.class, () -> BlockRange.parse("1", "0"));
    assertThrows(IllegalArgumentException.class, () -> BlockRange.parse("-1", "0"));
    assertThrows(
        IllegalArgumentException.class, () -> BlockRange.parse("0", "9999999999999999999"));
  }
}
