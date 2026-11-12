package com.example.demo.reconciliation.domain;

public record ReconciliationFinding(String kind, String payoutId, String hash, String reason) {
  public String identity() {
    return (payoutId == null ? "tx:" + hash : "payout:" + payoutId) + ":" + kind;
  }
}
