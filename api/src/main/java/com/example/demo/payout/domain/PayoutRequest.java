package com.example.demo.payout.domain;

import com.example.demo.common.exception.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record PayoutRequest(
    String merchantId, EthereumAddress recipientAddress, EthAmount amount, String idempotencyKey) {
  public PayoutRequest {
    if (merchantId == null || merchantId.isBlank() || merchantId.length() > 200) {
      throw new InvalidInputException("merchantId is required and must be at most 200 characters");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
      throw new InvalidInputException(
          "Idempotency-Key is required and must be at most 200 characters");
    }
    if (recipientAddress == null || amount == null) {
      throw new InvalidInputException("recipientAddress and amount are required");
    }
  }

  public String fingerprint() {
    // Versioned and length-prefixed merchant field; canonical address and wei.
    String canonical =
        "v1:"
            + merchantId.length()
            + ":"
            + merchantId
            + ":"
            + recipientAddress.value()
            + ":"
            + amount.wei();
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
