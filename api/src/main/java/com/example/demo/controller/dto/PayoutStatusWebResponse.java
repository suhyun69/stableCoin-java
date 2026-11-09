package com.example.demo.controller.dto;
public record PayoutStatusWebResponse(String id, String status, ChainWebResponse chain) {
    public record ChainWebResponse(String transactionHash, String nonce, String status, String confirmations) {}
}
