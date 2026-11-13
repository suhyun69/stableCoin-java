package com.example.demo.controller.dto;

public record PayoutWebResponse(
    String id, String merchantId, String from, String to, String amount, String status) {}
