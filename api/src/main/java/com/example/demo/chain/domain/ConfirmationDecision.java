package com.example.demo.chain.domain;

import java.util.Objects;

public record ConfirmationDecision(String kind, long confirmations, String reason) {
  public static ConfirmationDecision evaluate(
      ChainTransaction saved,
      EthTransfer observed,
      EthReceipt receipt,
      long head,
      String canonicalHash,
      long required) {
    if (required < 1) throw new IllegalArgumentException("confirmations must be positive");
    long confirmations = head < receipt.blockNumber() ? 0 : head - receipt.blockNumber() + 1;
    if (!Objects.equals(saved.hash(), receipt.hash())
        || !Objects.equals(receipt.blockHash(), canonicalHash)
        || confirmations == 0)
      return new ConfirmationDecision("REQUIRES_REVIEW", confirmations, "NON_CANONICAL_RECEIPT");
    if (observed == null
        || !Objects.equals(saved.hash(), observed.hash())
        || !saved.sender().equalsIgnoreCase(observed.from())
        || !saved.recipient().equalsIgnoreCase(observed.to())
        || !saved.amount().equals(observed.value())
        || saved.nonce() != observed.nonce()
        || !Objects.equals(observed.blockNumber(), receipt.blockNumber())
        || !Objects.equals(observed.blockHash(), receipt.blockHash()))
      return new ConfirmationDecision("REQUIRES_REVIEW", confirmations, "TRANSACTION_MISMATCH");
    if (!receipt.successful())
      return new ConfirmationDecision("FAILED", confirmations, "TRANSACTION_REVERTED");
    return new ConfirmationDecision(
        confirmations >= required ? "CONFIRMED" : "MINED", confirmations, "RECEIPT_VERIFIED");
  }
}
