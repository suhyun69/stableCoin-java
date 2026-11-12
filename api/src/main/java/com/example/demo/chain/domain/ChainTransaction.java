package com.example.demo.chain.domain;

import java.math.BigInteger;

public record ChainTransaction(
    String id,
    String payoutId,
    String sender,
    String recipient,
    BigInteger amount,
    long nonce,
    String status,
    String raw,
    String hash,
    Long includedBlock,
    long confirmations) {}
