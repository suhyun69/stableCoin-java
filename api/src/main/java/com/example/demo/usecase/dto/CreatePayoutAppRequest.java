package com.example.demo.usecase.dto;
public record CreatePayoutAppRequest(String merchantId, String recipientAddress, String amount, String idempotencyKey) {}
