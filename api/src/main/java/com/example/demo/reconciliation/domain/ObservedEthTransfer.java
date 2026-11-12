package com.example.demo.reconciliation.domain;

import java.math.BigInteger;

public record ObservedEthTransfer(
    String hash,
    String sender,
    String recipient,
    BigInteger amount,
    long nonce,
    long blockNumber,
    String blockHash,
    boolean successful,
    long confirmations) {}
