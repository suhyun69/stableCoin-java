package com.example.demo.payout.domain;

public record Payout(
    String id,
    String merchantId,
    String senderAddress,
    String recipientAddress,
    String amountAtomic,
    String requestFingerprint,
    PayoutStatus status) {}
