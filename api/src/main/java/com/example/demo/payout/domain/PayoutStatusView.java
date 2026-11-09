package com.example.demo.payout.domain;
public record PayoutStatusView(String id, String status, Chain chain) {
    public record Chain(String transactionHash, String nonce, String status, String confirmations) {}
}
