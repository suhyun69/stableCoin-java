package com.example.demo.usecase.dto;

public record PayoutAppResponse(
    String id, String merchantId, String from, String to, String amount, String status) {}
