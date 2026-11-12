package com.example.demo.usecase.dto;

public record PayoutAppResponse(
    String id, String merchantId, String recipientAddress, String amount, String status) {}
