package com.example.demo.usecase.dto;

public record PayoutStatusAppResponse(String id, String status, ChainAppResponse chain) {
  public record ChainAppResponse(
      String transactionHash, String nonce, String status, String confirmations) {}
}
