package com.example.demo.reconciliation.domain;

import java.math.BigInteger;

public record ReconciliationEntry(
    String payoutId,
    String hash,
    String sender,
    String recipient,
    BigInteger amount,
    long nonce,
    Long includedBlock,
    String payoutStatus,
    String chainStatus,
    boolean consistent) {}
