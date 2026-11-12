package com.example.demo.controller.dto;

public record PayoutWebResponse(
    String id, String merchantId, String recipientAddress, String amount, String status) {}
