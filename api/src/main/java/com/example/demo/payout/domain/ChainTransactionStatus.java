package com.example.demo.payout.domain;

public enum ChainTransactionStatus {
    CREATED, SIGNED, BROADCAST_UNKNOWN, PENDING,
    MINED_SUCCESS, MINED_REVERTED, CONFIRMED, FAILED
}
