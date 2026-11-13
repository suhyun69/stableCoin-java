package com.example.demo.usecase.dto;

public record CreatePayoutAppRequest(
    String merchantId, String from, String to, String amount, String idempotencyKey) {}
