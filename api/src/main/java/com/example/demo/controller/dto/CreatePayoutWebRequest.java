package com.example.demo.controller.dto;

import jakarta.validation.constraints.*;

public record CreatePayoutWebRequest(
    @NotBlank @Size(max = 200) String merchantId,
    @NotBlank @Pattern(regexp = "0x[0-9a-fA-F]{40}") String recipientAddress,
    @NotBlank @Size(max = 100) String amount) {}
